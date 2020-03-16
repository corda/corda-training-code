package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TokenContractK
import com.template.contracts.TokenContractK.Commands.Issue
import com.template.states.TokenStateK
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object IssueFlowsK {

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by the [TokenStateK.issuer] to issue multiple states where it is the only issuer.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would typically be called by RPC or by [FlowLogic.subFlow].
     */

    class Initiator(private val heldQuantities: List<Pair<Party, Long>>) : FlowLogic<SignedTransaction>() {

        /**
         * The only constructor that can be called from the CLI.
         * Started by the issuer to issue a single state.
         */
        constructor(holder: Party, quantity: Long) : this(listOf(Pair(holder, quantity)))

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // It is a design decision to have this flow initiated by the issuer.
            val issuer = ourIdentity
            val outputTokens = heldQuantities.map {
                TokenStateK(issuer = issuer, holder = it.first, quantity = it.second)
            }
            // It is better practice to precisely define the accepted notary instead of picking the first one in the
            // list of notaries
            val notary = serviceHub.networkMapCache.getNotary(Constants.desiredNotary)!!

            progressTracker.currentStep = GENERATING_TRANSACTION
            // The issuer is a required signer, so we express this here
            val txCommand = Command(Issue(), issuer.owningKey)
            val txBuilder = TransactionBuilder(notary)
                    .addCommand(txCommand)
            outputTokens.forEach { txBuilder.addOutputState(it, TokenContractK.TOKEN_CONTRACT_ID) }

            progressTracker.currentStep = SIGNING_TRANSACTION
            // We are the only issuer here, and the issuer's signature is required. So we sign.
            // There are no other signatures to collect.
            val fullySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = FINALISING_TRANSACTION
            val holderFlows = outputTokens
                    .map { it.holder }
                    // Remove duplicates as it would be an issue when initiating flows, at least.
                    // If we did not use a Stream we could for instance use a Set.
                    .distinct()
                    // Remove myself.
                    // I already know what I am doing so no need to inform myself with a separate flow.
                    .minus(issuer)
                    .map { initiateFlow(it) }

            // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
            // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
            // manually.
            serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(fullySignedTx))

            return subFlow(FinalityFlow(
                    fullySignedTx,
                    holderFlows,
                    FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            return subFlow(ReceiveFinalityFlow(counterpartySession))
        }
    }

}
