package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TokenContractK.Commands.Redeem
import com.template.states.TokenStateK
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object RedeemFlowsK {

    @StartableByRPC
    class InitiatorSums(
            private val notary: Party,
            private val inputTokenSums: List<TokenStateK>) : FlowLogic<SignedTransaction>() {

        @Suppress("ClassName")
        companion object {
            object FETCHING_TOKEN_STATES : ProgressTracker.Step("Fetching token states based on parameters.")
            object HANDING_TO_INITIATOR : ProgressTracker.Step("Handing to proper initiator.") {
                override fun childProgressTracker() = Initiator.tracker()
            }

            fun tracker() = ProgressTracker(
                    FETCHING_TOKEN_STATES,
                    HANDING_TO_INITIATOR)
        }

        override val progressTracker = tracker()

        override fun call(): SignedTransaction {
            progressTracker.currentStep = FETCHING_TOKEN_STATES

            val inputStates = inputTokenSums
                    .flatMap { serviceHub.vaultService.fetchWorthAtLeast(it, listOf(notary)) }

            // TODO handle a Move so as to have the exact amount to pass next instead of just losing everything.

            progressTracker.currentStep = HANDING_TO_INITIATOR
            return subFlow(Initiator(
                    inputStates,
                    HANDING_TO_INITIATOR.childProgressTracker()))
        }

    }

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by a [TokenStateK.holder] to redeem multiple states where it is one of the holders.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would be called by RPC or by [FlowLogic.subFlow]. In particular one that, given sums, fetches
     * states in the vault.
     */
    class Initiator(
            private val inputTokens: List<StateAndRef<TokenStateK>>,
            override val progressTracker: ProgressTracker = tracker()) : FlowLogic<SignedTransaction>() {

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

        @Suspendable
        override fun call(): SignedTransaction {
            progressTracker.currentStep = GENERATING_TRANSACTION
            // We can only make a transaction if all states have to be marked by the same notary.
            val notary = inputTokens
                    .map { it.state.notary }
                    .distinct()
                    .single()

            val otherSigners = inputTokens
                    .map { it.state.data }
                    // Keep a mixed list of issuers and holders.
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
            inputTokens.forEach { txBuilder.addInputState(it) }

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
    /**
     * When you create an initiator and an associated responder, you have no assurance that a peer will launch "your"
     * responder when you launch "your" initiator. Your peer may:
     *   - use a totally different one that nonetheless follows the same back and forth choreography.
     *   - use a sub-class in order to reuse the work you did on "your" responder.
     * Here we set up our responder to be able to be sub-classed by allowing others to introduce additional checks
     * on the transaction to be signed.
     * Additionally, when "your" responder is launched, you have no assurance that the peer that triggered the flow
     * used "your" initiator. The initiating peer may well have used a sub-class of "your" initiator.
     */
    open class Responder(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {

        /**
         * Peers can create sub-classes and extends the checks on the transaction by overriding this dummy function.
         * In particular, peers will be well advised to add logic here to control whether they really want this
         * transaction to happen.
         */
        protected open fun additionalChecks(stx: SignedTransaction) = Unit

        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // We add our internal check for clients that want to extend this feature.
                    additionalChecks(stx)
                    // Here have the checks that presumably all peers will want to have. It is opinionated and peers
                    // are free to not use this class if they want to.
                    requireThat {
                        // Here we only automatically check that it is technically satisfactory.
                        // We don't like signing irrelevant transactions. I must be relevant.
                        val relevant = stx.toLedgerTransaction(serviceHub, false)
                                .inputsOfType<TokenStateK>()
                                .any { it.issuer == ourIdentity || it.holder == ourIdentity }
                        "I must be relevant." using relevant
                    }
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }

}
