This represents the step where you have created your own fungible token.

## Design decisions

* A given transaction can only have a single command `Issue`, `Move` or `Redeem`. So it is not possible to reward an issuer `Issue`ing by `Move`ing other tokens to their benefit, for instance.
* We cannot have a `Move` transaction where the sums per issuer are greater than `Long.MAX_VALUE`. It would be possible to have a more complex evaluation that make such a transaction possible.