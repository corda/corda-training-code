package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TokenContractK.Commands.Redeem
import com.template.states.TokenStateK
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object RedeemFlowsK {

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started either by a [TokenStateK.holder] to redeem multiple states where it is one of the holders.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would typically be called by RPC or by [FlowLogic.subFlow].
     */
    class Initiator(private val inputTokens: List<StateRef>) : FlowLogic<SignedTransaction>() {

        /**
         * The only constructor that can be called from the CLI.
         * Started by the holder to redeem a single state.
         */
        constructor(hash: SecureHash, index: Int) : this(listOf(StateRef(hash, index)))

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
            val inputStateTokens = inputTokens
                    .map { serviceHub.toStateAndRef<TokenStateK>(it) }
            // We can only make a transaction if all states have to be marked by the same notary.
            val notary = inputTokens
                    .map { serviceHub.validatedTransactions.getTransaction(it.txhash)!!.notary!! }
                    .distinct()
                    .single()

            val otherSigners = inputStateTokens
                    .map { it.state.data }
                    .flatMap { listOf(it.issuer, it.holder) }
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    .distinct()
                    // Remove myself.
                    .minus(ourIdentity)

            // The issuers and holders are required signers, so we express this here.
            val txCommand = Command(
                    Redeem(),
                    otherSigners.plus(ourIdentity).map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addCommand(txCommand)
            inputStateTokens.forEach { txBuilder.addInputState(it) }

            progressTracker.currentStep = SIGNING_TRANSACTION
            // We are but one of the signers.
            val partlySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = GATHERING_SIGS
            // We need to gather the signatures of all issuers and all holders, except ourselves.
            val otherFlows = otherSigners
                    .map { initiateFlow(it) }
            val fullySignedTx =
                    if (otherFlows.isEmpty()) partlySignedTx
                    else subFlow(CollectSignaturesFlow(
                            partlySignedTx,
                            otherFlows,
                            GATHERING_SIGS.childProgressTracker()))

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(FinalityFlow(
                    fullySignedTx,
                    otherFlows,
                    FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // Notice that there is still a security risk here as my node can be asked to sign without my
                    // human knowledge.
                    // I must be relevant. We don't like signing irrelevant transactions.
                    val relevant = stx.toLedgerTransaction(serviceHub, false)
                            .inputsOfType<TokenStateK>()
                            .any { it.issuer == ourIdentity || it.holder == ourIdentity }
                    "I must be relevant." using relevant
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }

}
