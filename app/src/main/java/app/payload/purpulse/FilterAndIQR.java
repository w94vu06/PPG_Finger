package app.payload.purpulse;

import android.os.Build;

import java.util.Arrays;

public class FilterAndIQR {
    long[] nonZeroValues;
    public long[] IQR(long[] RRI) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            double[] arr = Arrays.stream(RRI).asDoubleStream().toArray();
            Arrays.sort(arr);
            double q1 = findMedian(arr, 0, arr.length/2-1);
            double q3 = findMedian(arr, arr.length/2 + arr.length%2, arr.length-1);
            // 計算 IQR
            double iqr = q3 - q1;

            // 計算上下界
            double upperBound = q3 + 1.5 * iqr;
            double lowerBound = q1 - 1.5 * iqr;

            // 將超過上下界的值設為0
            for (int i = 0; i < RRI.length; i++) {
                if (RRI[i] > upperBound || RRI[i] < lowerBound) {
                    RRI[i] = 0;
                }
            }

            // 找出所有不為0的值的平均數
            int count = 0;
            nonZeroValues = new long[RRI.length];
            for(int i = 0; i < RRI.length; i++) {
                if (RRI[i] != 0) {
                    nonZeroValues[count] = RRI[i];
                    count++;
                }
            }
        }
        return nonZeroValues ;
    }

    public static double findMedian(double[] arr, int start, int end) {
        int len = end - start + 1;
        int mid = start + len/2;
        if (len % 2 == 0) {
            return (arr[mid-1] + arr[mid]) / 2.0;
        } else {
            return arr[mid];
        }
    }
}
