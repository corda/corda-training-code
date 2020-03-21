package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TokenContractK
import com.template.contracts.TokenContractK.Commands.Move
import com.template.states.TokenStateK
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object MoveFlowsK {

    @CordaSerializable
    /**
     * A participant needs to sign, an observer only needs to receive the result.
     */
    enum class TransactionRole { PARTICIPANT, OBSERVER }

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by a [TokenStateK.holder] to move multiple states where it is one of the holders.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would be called by RPC or by [FlowLogic.subFlow]. In particular one that, given sums, fetches
     * states in the vault.
     */
    class Initiator(
            private val inputTokens: List<StateAndRef<TokenStateK>>,
            private val outputTokens: List<TokenStateK>) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    VERIFYING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_TRANSACTION
            // We can only make a transaction if all states have to be marked by the same notary.
            val notary = inputTokens
                    .map { it.state.notary }
                    .distinct()
                    .single()

            val signers = inputTokens
                    // Only the input holder is necessary on a Move.
                    .map { it.state.data.holder }
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .distinct()
                    .also { require(it.contains(ourIdentity)) { "I must be a holder." } }
                    // Remove myself.
                    .minus(ourIdentity)

            // The issuers and holders are required signers, so we express this here.
            val txCommand = Command(
                    Move(),
                    signers.plus(ourIdentity).map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addCommand(txCommand)
            inputTokens.forEach { txBuilder.addInputState(it) }
            outputTokens.forEach { txBuilder.addOutputState(it, TokenContractK.TOKEN_CONTRACT_ID) }

            progressTracker.currentStep = SIGNING_TRANSACTION
            // We are but one of the signers.
            val partlySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            // We need to gather the signatures of all issuers and all holders, except ourselves.
            val signerFlows = signers
                    .map { initiateFlow(it) }
                    // Prime these responders to act in a signer type of way.
                    .onEach { it.send(TransactionRole.PARTICIPANT) }
            val fullySignedTx =
                    if (signerFlows.isEmpty()) partlySignedTx
                    else subFlow(CollectSignaturesFlow(
                            partlySignedTx,
                            signerFlows,
                            GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = FINALISING_TRANSACTION
            // The new holders that are not signers and still need to be informed.
            val newHolderFlows = outputTokens
                    .map { it.holder }
                    .distinct()
                    .minus(signers)
                    .minus(ourIdentity)
                    .map { initiateFlow(it) }
                    // Prime these responders to act in a holder type of way.
                    .onEach { it.send(TransactionRole.OBSERVER) }
            return subFlow(FinalityFlow(
                    fullySignedTx,
                    signerFlows.plus(newHolderFlows),
                    FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    open class Responder(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        protected open fun responderCheck(stx: SignedTransaction) = Unit

        @Suspendable
        override fun call(): SignedTransaction {
            val myRole = counterpartySession.receive<TransactionRole>().unwrap { it }
            val txId = when (myRole) {
                // We do not need to sign.
                TransactionRole.OBSERVER -> null
                TransactionRole.PARTICIPANT -> {
                    val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                        override fun checkTransaction(stx: SignedTransaction) {
                            responderCheck(stx)
                            requireThat {
                                // Notice that there is still a security risk here as my node can be asked to sign
                                // without my human knowledge.
                                // I must be relevant. We don't like signing irrelevant transactions.
                                val relevant = stx.toLedgerTransaction(serviceHub, false)
                                        .inputsOfType<TokenStateK>()
                                        .any { it.holder == ourIdentity }
                                "I must be relevant." using relevant
                                // We add our internal check for clients that want to extend this feature.
                            }
                        }
                    }
                    subFlow(signTransactionFlow).id
                }
            }

            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }

}