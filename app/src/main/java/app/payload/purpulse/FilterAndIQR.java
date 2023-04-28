package app.payload.purpulse;

import android.os.Build;
import android.util.Log;

import java.util.Arrays;

public class FilterAndIQR {
    double rmssd;
    double sdnn;
    long[] nonZeroValues;

    public long[] IQR(long[] RRI) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            double[] arr = Arrays.stream(RRI).asDoubleStream().toArray();
            Arrays.sort(arr);
            double q1 = findMedian(arr, 0, arr.length / 2 - 1);
            double q3 = findMedian(arr, arr.length / 2 + arr.length % 2, arr.length - 1);
            // 計算 IQR
            double iqr = q3 - q1;

            // 計算上下界
            double upperBound = q3 + 1.2 * iqr;
            double lowerBound = q1 - 1.2 * iqr;

            // 將超過上下界的值設為0
            for (int i = 0; i < RRI.length; i++) {
                if (RRI[i] > upperBound || RRI[i] < lowerBound) {
                    RRI[i] = 0;
                }
            }

            // 找出所有不為0的值的平均數
            int count = 0;
            nonZeroValues = new long[RRI.length];
            for (int i = 0; i < RRI.length; i++) {
                if (RRI[i] != 0) {
                    nonZeroValues[count] = RRI[i];
                    Log.d("nonZERO", " : "+nonZeroValues[count]);
                    count++;
                }
            }
        }
        return nonZeroValues;
    }

    public static double findMedian(double[] arr, int start, int end) {
        int len = end - start + 1;
        int mid = start + len / 2;
        if (len % 2 == 0) {
            return (arr[mid - 1] + arr[mid]) / 2.0;
        } else {
            return arr[mid];
        }
    }

    // 計算SDNN
    public double calculateSDNN(long[] rrIntervals) {
        double mean = calculateMean(rrIntervals);
        double sumOfSquares = 0.0;
        for (int i = 0; i < rrIntervals.length; i++) {
            sumOfSquares += Math.pow(rrIntervals[i] - mean, 2);
        }
        double variance = sumOfSquares / (rrIntervals.length - 1);
        sdnn = Math.sqrt(variance);
        return sdnn / 1000;
    }

    // 計算RMSSD
    public double calculateRMSSD(long[] rrIntervals) {
        int n = rrIntervals.length;
        long[] rrIntervalsNoTail = Arrays.copyOfRange(rrIntervals, 0, n - 1);

        double sumOfDifferencesSquared = 0.0;
        for (int i = 1; i < rrIntervalsNoTail.length; i++) {
            double difference = rrIntervalsNoTail[i] - rrIntervalsNoTail[i - 1];
            sumOfDifferencesSquared += difference * difference;
        }

        rmssd = sumOfDifferencesSquared / (rrIntervals.length - 1);
        return Math.sqrt(rmssd);
    }

    // 計算平均值
    public double calculateMean(long[] values) {
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            sum += (double) values[i];
        }
        double mean = sum / values.length;
        return mean;
    }
}
