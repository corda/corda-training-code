package com.template.car;

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.Party;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@BelongsToContract(CarTokenContract.class)
public class CarTokenType extends EvolvableTokenType {

    public static final int FRACTION_DIGITS = 0;
    @NotNull
    private final List<Party> maintainers;
    @NotNull
    private final UniqueIdentifier uniqueIdentifier;
    @NotNull
    private final String vin;
    @NotNull
    private final String make;
    private final long mileage;
    private final long price;

    public CarTokenType(@NotNull final List<Party> maintainers, @NotNull final UniqueIdentifier uniqueIdentifier,
                        @NotNull final String vin, @NotNull final String make,
                        final long mileage, final long price) {
        Validate.notNull(maintainers, "Maintainer cannot be empty.");
        Validate.notNull(uniqueIdentifier, "Unique identifier cannot be empty.");
        Validate.notBlank(vin, "VIN cannot be empty.");
        Validate.notBlank(make, "Make cannot be empty.");
        Validate.isTrue(mileage >= 0, "Mileage cannot be negative.");
        Validate.isTrue(price > 0, "Price cannot be 0.");
        this.maintainers = maintainers;
        this.uniqueIdentifier = uniqueIdentifier;
        this.vin = vin;
        this.make = make;
        this.mileage = mileage;
        this.price = price;
    }

    @Override
    public int getFractionDigits() {
        return FRACTION_DIGITS;
    }

    @NotNull
    @Override
    public List<Party> getMaintainers() {
        return maintainers;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return uniqueIdentifier;
    }

    @NotNull
    public String getVin() {
        return vin;
    }

    @NotNull
    public String getMake() {
        return make;
    }

    public long getMileage() {
        return mileage;
    }

    public long getPrice() {
        return price;
    }

    // We require `equals()` and `hashCode` to properly group Tokens, and also to be able to use
    // them in a HashMap.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CarTokenType that = (CarTokenType) o;
        return getFractionDigits() == that.getFractionDigits() &&
                Double.compare(that.getMileage(), getMileage()) == 0 &&
                Double.compare(that.getPrice(), getPrice()) == 0 &&
                maintainers.equals(that.maintainers) &&
                uniqueIdentifier.equals(that.uniqueIdentifier) &&
                getVin().equals(that.getVin()) &&
                getMake().equals(that.getMake());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFractionDigits(), maintainers, uniqueIdentifier, getVin(), getMake(),
                getMileage(), getPrice());
    }
}
