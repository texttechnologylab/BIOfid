package BIOfid.Extraction;

import BIOfid.OCR.AbstractOCRParser;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRDocument;
import org.texttechnologylab.annotation.ocr.OCRToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static BIOfid.Utility.Util.writeToFile;
import static org.apache.uima.fit.util.JCasUtil.*;

/**
 * Created on 14.02.2019.
 */
public class TextFromXMI extends AbstractOCRParser {
	
	public static void main(String[] args) {
		String sInputPath = args[0];
		String sOutputPath = args[1];
		
		
		Stream<File> fileStream = Streams.stream(Files.fileTraverser().breadthFirst(Paths.get(sInputPath).toFile())).filter(File::isFile);
		final long size = fileStream.count();
		int[] count = {0};
//			for (File file : files) {
		ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
		try {
			forkJoinPool.submit(() ->
					fileStream.parallel().forEach(file -> {
						try {
							//				List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);
							JCas jCas = JCasFactory.createJCas();
							CasIOUtil.readXmi(jCas, file);
							if (jCas.getDocumentText().isEmpty())
								return;
							
							String content = getValidText(jCas);
							
							String outFileName = file.getName().replaceAll("\\.xm[il]", ".txt");
							System.out.printf("\r%d/%d Writing %s (running threads: %d, pool size: %d)",
									count[0]++, size, outFileName, forkJoinPool.getRunningThreadCount(), forkJoinPool.getPoolSize());
							writeToFile(Paths.get(sOutputPath, outFileName), content);
						} catch (UIMAException | IOException e) {
							e.printStackTrace();
						}
					})).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	public static String getValidText(JCas jCas) {
		Collection<OCRDocument> ocrDocuments = select(jCas, OCRDocument.class);
		ocrDocuments.removeAll(indexCovered(jCas, OCRDocument.class, OCRDocument.class).keySet());
		
		ImmutableMap<OCRDocument, Collection<OCRToken>> documentCoveringToken = ImmutableMap.copyOf(indexCovered(jCas, OCRDocument.class, OCRToken.class));
		
		ImmutableSet<OCRToken> tokenCovering = ImmutableSet.copyOf(indexCovering(jCas, OCRToken.class, OCRToken.class).keySet());
		ImmutableSet<OCRToken> anomalies = ImmutableSet.copyOf(indexCovered(jCas, Anomaly.class, OCRToken.class).entrySet().stream()
				.filter(entry -> entry.getKey().getCategory().equals("BioFID_Garbage_Line_Anomaly"))
				.map(Map.Entry::getValue)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet()));
		
		StringBuilder retStringBuilder = new StringBuilder();
		
		for (OCRDocument ocrDocument : ocrDocuments) {
			if (!documentCoveringToken.containsKey(ocrDocument) || ocrDocument.getCoveredText().isEmpty())
				continue;
			for (OCRToken ocrToken : documentCoveringToken.get(ocrDocument)) {
				if (tokenCovering.contains(ocrToken) || anomalies.contains(ocrToken)) continue;
				retStringBuilder.append(ocrToken.getCoveredText()).append(" ");
			}
		}
		
		return retStringBuilder.toString();
	}
	
}
