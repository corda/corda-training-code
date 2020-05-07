package com.template.proposal.state;

import com.r3.corda.lib.tokens.contracts.states.AbstractToken;
import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class SalesProposalContract implements Contract {

    public static final String SALES_PROPOSAL_CONTRACT_ID = "com.template.proposal.state.SalesProposalContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {
        final List<Command<Commands>> commands = tx.commandsOfType(Commands.class);
        final List<StateAndRef<AbstractToken>> inRefs = tx.referenceInputRefsOfType(AbstractToken.class);
        final List<StateAndRef<SalesProposal>> inSalesProposals = tx.inRefsOfType(SalesProposal.class);
        final List<StateAndRef<SalesProposal>> outSalesProposals = tx.outRefsOfType(SalesProposal.class);
        final List<StateAndRef<NonFungibleToken>> inNFTokens = tx.inRefsOfType(NonFungibleToken.class);
        final List<StateAndRef<NonFungibleToken>> outNFTokens = tx.outRefsOfType(NonFungibleToken.class);
        final List<StateAndRef<FungibleToken>> outFTokens = tx.outRefsOfType(FungibleToken.class);

        requireThat(req -> {
            req.using("There should be a single sales proposal command",
                    commands.size() == 1);
            final Command<Commands> command = commands.get(0);

            if (command.getValue() instanceof Commands.Offer) {
                req.using("There should be no sales proposal inputs on offer",
                        inSalesProposals.isEmpty());
                req.using("There should be a single sales proposal output on offer",
                        outSalesProposals.size() == 1);
                req.using("There should be a single reference input token on offer",
                        inRefs.size() == 1);
                final StateAndRef<AbstractToken> refToken = inRefs.get(0);
                final SalesProposal proposal = outSalesProposals.get(0).getState().getData();
                req.using("The reference token should match the sales proposal output asset",
                        proposal.isSameAsset(refToken));
                req.using("The sales proposal offer price should not be zero",
                        0 < proposal.getPrice().getQuantity());
                req.using("The seller should be the only signer on the offer",
                        Collections.singletonList(proposal.getSeller().getOwningKey()).equals(command.getSigners()));

            } else if (command.getValue() instanceof Commands.Accept) {
                req.using("There should be a single input sales proposal on accept",
                        inSalesProposals.size() == 1);
                req.using("There should be no sales proposal outputs on accept",
                        outSalesProposals.isEmpty());
                final SalesProposal proposal = inSalesProposals.get(0).getState().getData();
                final List<StateAndRef<NonFungibleToken>> candidates = inNFTokens.stream()
                        .filter(proposal::isSameAsset)
                        .collect(Collectors.toList());
                req.using("The asset should be an input on accept", candidates.size() == 1);
                final List<NonFungibleToken> boughtAsset = outNFTokens.stream()
                        .map(it -> it.getState().getData())
                        .filter(it -> it.getLinearId().equals(proposal.getAssetId()))
                        .collect(Collectors.toList());
                req.using("The asset should be held by buyer in output on accept",
                        boughtAsset.size() == 1 && boughtAsset.get(0).getHolder().equals(proposal.getBuyer()));
                final long sellerPayment = outFTokens.stream()
                        .map(it -> it.getState().getData())
                        .filter(it -> it.getHolder().equals(proposal.getSeller()))
                        .filter(it -> it.getIssuedTokenType().equals(proposal.getPrice().getToken()))
                        .map(it -> it.getAmount().getQuantity())
                        .reduce(0L, Math::addExact);
                req.using("The seller should be paid the agreed amount in the agreed issued token on accept",
                        proposal.getPrice().getQuantity() <= sellerPayment);
                req.using("The buyer should be the only signer on accept",
                        Collections.singletonList(proposal.getBuyer().getOwningKey()).equals(command.getSigners()));

            } else if (command.getValue() instanceof Commands.Reject) {
                req.using("There should be a single input sales proposal on reject",
                        inSalesProposals.size() == 1);
                req.using("There should be no sales proposal outputs on reject",
                        outSalesProposals.isEmpty());
                final SalesProposal proposal = inSalesProposals.get(0).getState().getData();
                req.using("The seller or the buyer or both should be signers",
                        command.getSigners().contains(proposal.getSeller().getOwningKey()) ||
                                command.getSigners().contains(proposal.getBuyer().getOwningKey()));
                req.using("Only the seller or the buyer or both should be signers",
                        Arrays.asList(proposal.getSeller().getOwningKey(), proposal.getBuyer().getOwningKey())
                                .containsAll(command.getSigners()));
            } else {
                throw new IllegalArgumentException("Unknown command: " + command.getValue());
            }

            return null;
        });
    }

    public interface Commands extends CommandData {
        class Offer implements Commands {
        }

        class Accept implements Commands {
        }

        class Reject implements Commands {
        }
    }
}
