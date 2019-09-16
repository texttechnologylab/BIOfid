package BIOfid.Extraction;

import BIOfid.AbstractRunner;
import com.google.common.collect.ImmutableSet;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRDocument;
import org.texttechnologylab.annotation.ocr.OCRToken;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static BIOfid.Utility.Util.processDocument;
import static BIOfid.Utility.Util.writeToFile;
import static java.lang.System.exit;
import static org.apache.uima.fit.util.JCasUtil.*;

/**
 * Created on 21.03.19
 */
public class ExtractSingleDocument extends AbstractRunner {
	static String sXmiPath;
	static String sDocumentId;
	static String sOutputPath;
	static String sRawPath = null;
	
	public static void main(String[] args) {
		try {
			getParams(args);
			int index;
			
			index = params.indexOf("-i");
			if (index > -1) {
				sXmiPath = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -i!");
			}
			
			index = params.indexOf("-d");
			if (index > -1) {
				sDocumentId = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -d!");
			}
			
			index = params.indexOf("-o");
			if (index > -1) {
				sOutputPath = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -o!");
			}
			
			index = params.indexOf("--raw");
			if (index > -1) {
				sRawPath = params.get(index + 1);
			}
			
			
			// Get Document
			try {
				JCas jCas = JCasFactory.createJCas();
				File aFile = new File(sXmiPath);
				if (!aFile.exists() || !aFile.isFile()) {
					System.err.println("The given XMI file path is invalid.");
					exit(1);
					return;
				}
				System.out.println("Reading XMI..");
				CasIOUtil.readXmi(jCas, aFile);
				if (jCas.getDocumentText().isEmpty()) {
					System.err.println("The given XMI has no document text.");
					exit(2);
					return;
				}
				System.out.println("Getting single document from id..");
				Optional<OCRDocument> optional = JCasUtil.select(jCas, OCRDocument.class).stream().filter(d -> d.getDocumentname().equals(sDocumentId)).findFirst();
				if (optional.isPresent()) {
					OCRDocument ocrDocument = optional.get();
					
					Map<OCRDocument, Collection<OCRToken>> documentTokenMap = indexCovered(jCas, OCRDocument.class, OCRToken.class);
					
					ImmutableSet<OCRToken> tokenCovering = ImmutableSet.copyOf(indexCovering(jCas, OCRToken.class, OCRToken.class).keySet());
					ImmutableSet<OCRToken> anomalies = ImmutableSet.copyOf(indexCovered(jCas, Anomaly.class, OCRToken.class).entrySet().stream()
							.filter(entry -> entry.getKey().getCategory().equals("BioFID_Garbage_Line_Anomaly"))
							.map(Map.Entry::getValue)
							.flatMap(Collection::stream)
							.collect(Collectors.toSet()));
					
					StringBuilder documentString = new StringBuilder();
					
					processDocument(ocrDocument, documentTokenMap, tokenCovering, anomalies, documentString);
					
					System.out.println("Writing document..");
					
					writeToFile(Paths.get(sOutputPath), documentString.toString());
					
					if (sRawPath != null) {
						writeToFile(Paths.get(sRawPath), ocrDocument.getCoveredText());
					}
				} else {
					System.err.println("The document could not be found in the given XMI.");
					exit(3);
				}
			} catch (UIMAException | IOException e) {
				e.printStackTrace();
			}
		} catch (MissingArgumentException e) {
			e.printStackTrace();
		}
	}
}
