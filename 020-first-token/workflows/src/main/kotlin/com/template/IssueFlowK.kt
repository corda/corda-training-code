package com.template

import co.paralleluniverse.fibers.Suspendable
import com.template.contracts.TokenContractK
import com.template.contracts.TokenContractK.Commands.Issue
import com.template.states.TokenStateK
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object IssueFlowK {

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by the issuer to issue multiple states where it is the only issuer.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     */
    class Initiator(private val heldQuantities: List<Pair<Party, Long>>) : FlowLogic<SignedTransaction>() {

        constructor(heldQuantity: Pair<Party, Long>) : this(listOf(heldQuantity))
        // Started by the issuer to issue a single state.
        constructor(holder: Party, quantity: Long) : this(Pair(holder, quantity))

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
            val issuer = ourIdentity
            val outputTokens = heldQuantities.map {
                TokenStateK(issuer = issuer, holder = it.first, quantity = it.second)
            }
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            progressTracker.currentStep = GENERATING_TRANSACTION
            val txCommand = Command(Issue(), ourIdentity.owningKey)
            val txBuilder = TransactionBuilder(notary)
                    .addCommand(txCommand)
            outputTokens.forEach { txBuilder.addOutputState(it, TokenContractK.TOKEN_CONTRACT_ID) }

            progressTracker.currentStep = SIGNING_TRANSACTION
            val fullySignedTx = serviceHub.signInitialTransaction(txBuilder)

            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            progressTracker.currentStep = FINALISING_TRANSACTION
            val holderFlows = outputTokens
                    .map { it.holder }
                    // Duplicates would be an issue when initiating flows, at least.
                    .distinct()
                    // I do not need to inform myself separately.
                    .minus(ourIdentity)
                    .map { initiateFlow(it) }

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
