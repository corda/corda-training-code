package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TokenContractK
import com.template.contracts.TokenContractK.Commands.Move
import com.template.states.TokenStateK
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object MoveFlowsK {

    @CordaSerializable
    /**
     * The different transaction roles expected of the responder.
     * A signer needs to sign, a participant only needs to receive the result.
     */
    enum class TransactionRole { SIGNER, PARTICIPANT }

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by a [TokenStateK.holder] to move multiple states where it is one of the holders.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would be called by RPC or by [FlowLogic.subFlow]. In particular one that, given sums, fetches
     * states in the vault.
     */
    class Initiator @JvmOverloads constructor(
            private val inputTokens: List<StateAndRef<TokenStateK>>,
            private val outputTokens: List<TokenStateK>,
            override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_TRANSACTION
            // We can only make a transaction if all states have to be marked by the same notary.
            val notary = inputTokens
                    .map { it.state.notary }
                    // This gets rid of duplicates.
                    .distinct()
                    .single()

            val allSigners = inputTokens
                    // Only the input holder is necessary on a Move.
                    .map { it.state.data.holder }
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .toSet()
            // We don't want to sign transactions where our signature is not needed.
            if (!allSigners.contains(ourIdentity)) throw FlowException("I must be a holder.")

            // The issuers and holders are required signers, so we express this here.
            val txCommand = Command(
                    Move(),
                    allSigners.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addCommand(txCommand)
            inputTokens.forEach { txBuilder.addInputState(it) }
            outputTokens.forEach { txBuilder.addOutputState(it, TokenContractK.TOKEN_CONTRACT_ID) }

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = SIGNING_TRANSACTION
            // We are but one of the signers.
            val partlySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            // We need to gather the signatures of all issuers and all holders, except ourselves.
            val signerFlows = allSigners
                    // Remove myself.
                    .minus(ourIdentity)
                    .map { initiateFlow(it) }
                    // Prime these responders to act in a signer type of way.
                    .onEach { it.send(TransactionRole.SIGNER) }
            val fullySignedTx =
                    if (signerFlows.isEmpty()) partlySignedTx
                    else subFlow(CollectSignaturesFlow(
                            partlySignedTx,
                            signerFlows,
                            GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = FINALISING_TRANSACTION
            // The new holders that are not signers and still need to be informed.
            val newHolderFlows = outputTokens
                    .map { it.holder }
                    .distinct()
                    // The signers are being handled in the other flows.
                    .minus(allSigners)
                    // We don't need to inform ourselves.
                    .minus(ourIdentity)
                    .map { initiateFlow(it) }
                    // Prime these responders to act in a holder type of way.
                    .onEach { it.send(TransactionRole.PARTICIPANT) }
            return subFlow(FinalityFlow(
                    fullySignedTx,
                    // All of them need to finalise.
                    signerFlows.plus(newHolderFlows),
                    FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    open class Responder(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object RECEIVING_ROLE : ProgressTracker.Step("Receiving role to impersonate.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.") {
                override fun childProgressTracker() = SignTransactionFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    RECEIVING_ROLE,
                    SIGNING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = RECEIVING_ROLE
            val myRole = counterpartySession.receive<TransactionRole>().unwrap { it }

            progressTracker.currentStep = SIGNING_TRANSACTION
            val txId = when (myRole) {
                // We do not need to sign.
                TransactionRole.PARTICIPANT -> null
                TransactionRole.SIGNER -> {
                    val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                        override fun checkTransaction(stx: SignedTransaction) {
                            // Notice that there is still a security risk here as my node can be asked to sign
                            // without my human knowledge.
                            // I must be relevant. We don't like signing irrelevant transactions.
                            val relevant = stx.toLedgerTransaction(serviceHub, false)
                                    .inputsOfType<TokenStateK>()
                                    .any { it.holder == ourIdentity }
                            if (!relevant) throw FlowException("I must be relevant.")
                        }
                    }
                    subFlow(signTransactionFlow).id
                }
            }

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }

}