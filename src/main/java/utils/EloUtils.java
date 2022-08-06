package utils;

public class EloUtils
{
    public static final int K = 30;

    // https://www.cantorsparadise.com/the-mathematics-of-elo-ratings-b6bfc9ca1dba
    public static float probability(float rating1, float rating2) {
        return 1.0f / (1.0f + ((float) Math.pow(10, (rating1 - rating2) / 400f)));
    }

    public static float ratingWon(float rating, float probability) {
        return rating + K * (1f - probability);
    }

    public static float ratingLost(float rating, float probability) {
        return rating - K * probability;
    }

    public static void main(String[] args) {
        System.out.println(probability(1000, 1200));
    }
}
