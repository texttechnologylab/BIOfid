import com.google.common.base.Stopwatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static BIOfid.Utility.Util.countMatches;
import static BIOfid.Utility.Util.tokenPattern;

/**
 * Created on 26.02.2019.
 */
public class PerformanceTests {

//	@Test   // tokenPattern is 50% faster than alternatives
	public void testTokenDetection() {
		try {
			List<String> strings = Files.readAllLines(Paths.get("src/test/out/Biodiversity/9088333.txt"));

			long elapsedA = 0L;
			long elapsedAA = 0L;
			long elapsedB = 0L;
			int totalA = 0;
			int totalAA = 0;
			int totalB = 0;

			Pattern pattern = Pattern.compile("[^\\p{L}\\p{P}\\p{Sm}\\p{N}\\p{Sc}♂♀¬°½±^]+", Pattern.UNICODE_CHARACTER_CLASS);

			int iter = 1000;
			for (int i = 0; i < iter; i++) {
				totalA = 0;
				totalAA = 0;
				totalB = 0;

				Stopwatch stopwatchA = Stopwatch.createStarted();
				for (String line : strings) {
					totalA += countMatches(tokenPattern.matcher(line));
				}
				stopwatchA.stop();
				elapsedA += stopwatchA.elapsed().toMillis();

				Stopwatch stopwatchAA = Stopwatch.createStarted();
				for (String line : strings) {
					totalAA += countMatches(pattern.matcher(line));
				}
				stopwatchAA.stop();
				elapsedAA += stopwatchAA.elapsed().toMillis();

				Stopwatch stopwatchB = Stopwatch.createStarted();
				for (String line : strings) {
					totalB += line.split("[^\\p{L}\\p{P}\\p{Sm}\\p{N}\\p{Sc}♂♀¬°½±^]+").length;
				}
				stopwatchB.stop();
				elapsedB += stopwatchB.elapsed().toMillis();
			}

			System.out.printf("totalA:%d, timeA: %02.2fms\ntotalAA:%d, timeAA: %02.2fms\ntotalB:%d, timeB: %02.2fms", totalA, elapsedA / (1.0 * iter), totalAA, elapsedAA / (1.0 * iter), totalB, elapsedB / (1.0 * iter));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
