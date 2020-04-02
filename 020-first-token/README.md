This represents the step where you have created your own fungible token.

## Design decisions

* We do not assume we need to inform the issuer of the transaction. This not only increases the privacy of transactions, but it also prevents the issuer from being flooded with minute transactions. Of course, when time comes to `Redeem`, the issuer needs to know the whole transaction chain, so privacy is lost there, and the issuer receives a large transaction history.
* A given transaction can only have a single command `Issue`, `Move` or `Redeem`. So it is not possible to reward an issuer `Issue`ing by `Move`ing other tokens to their benefit, for instance.
* We cannot have a `Move` transaction where the sums per issuer are greater than `Long.MAX_VALUE`. It would be possible to have a more complex evaluation that make such a transaction possible.
* We need to collect the `issuer`'s signature when `Redeem`ing. This is a design decision and depends on your specs. If you are ok with the `owner` being the only one necessary to redeem, then you can code it as such. In our case, we could say that the issuer wants to control the total supply, and so wants control over `Issue` and `Redeem` actions. That makes sense in the case of Federal Reserve dollars, air miles or casino chips.
* The issue flow can issue small amounts of token states to the same holder several times, and is not limited to issue 1 state per holder. This can come in handy if we want to bind those states to other actions in parallel.

## Preparation

We decided to delegate build, run and test to Gradle so that the configuration is not shared between `build.gradle` and `.idea` files.
