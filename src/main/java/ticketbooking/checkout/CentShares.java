package ticketbooking.checkout;

public final class CentShares {

    private CentShares() {
    }

    public static int[] split(int amount, int[] weights, long[] ticketIds) {
        int count = weights.length;
        int[] shares = new int[count];
        if (count == 0 || amount == 0) {
            return shares;
        }
        long total = 0;
        int lowest = 0;
        for (int index = 0; index < count; index++) {
            total += weights[index];
            if (ticketIds[index] < ticketIds[lowest]) {
                lowest = index;
            }
        }
        if (total == 0) {
            shares[lowest] = amount;
            return shares;
        }
        int assigned = 0;
        for (int index = 0; index < count; index++) {
            shares[index] = (int) ((amount * (long) weights[index]) / total);
            assigned += shares[index];
        }
        shares[lowest] += amount - assigned;
        return shares;
    }
}
