import BIOfid.OCR.PageProcessEngine;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.SpellingAnomaly;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.chunk.Chunk;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.*;

public class PageProcessEngineTest {
	
	final boolean printAnnotations = true;
	
	@Test
	public void testTokenizationTable() throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(new File("src/test/resources/Biodiversity/9088917/9088369/9031004/0046_9028573.xml")))) {
			xml = br.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.printf("Text length: %d\n", xml.length());
		
		// Create a new Engine Description.
		testTokenization(xml);
	}
	
	@Test
	public void testTokenization47() throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(new File("src/test/resources/Biodiversity/9088917/9088369/9031004/0047_9028574.xml")))) {
			xml = br.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.printf("Text length: %d\n", xml.length());
		testTokenization(xml);
		
	}
	
	@Test
	public void testTokenization48() throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(new File("src/test/resources/Biodiversity/9088917/9088369/9031004/0048_9028575.xml")))) {
			xml = br.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.printf("Text length: %d\n", xml.length());
		testTokenization(xml);
		
	}
	
	@Test
	public void testTokenization49() throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(new File("src/test/resources/Biodiversity/9088917/9088369/9031004/0049_9028576.xml")))) {
			xml = br.lines().collect(Collectors.joining("\n"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.printf("Text length: %d\n", xml.length());
		testTokenization(xml);
		
	}
	
	private void testTokenization(String xml) throws UIMAException {
		// Create a new Engine Description.
		AnalysisEngineDescription pageParser = createEngineDescription(PageProcessEngine.class,
				PageProcessEngine.INPUT_XML, xml,
				PageProcessEngine.PARAM_USE_LANGUAGE_TOOL, true,
				PageProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 90,
				PageProcessEngine.PARAM_DICT_PATH, "src/test/resources/Leipzig40MT2010_lowered.5.vocab");
		
		// Create a new JCas - "Holder"-Class for Annotation.
		JCas inputCas = JCasFactory.createJCas();
		
		// Pipeline
		SimplePipeline.runPipeline(inputCas, pageParser);

//		System.out.println();
//		System.out.flush();
//
//		for (Chunk block : select(inputCas, Chunk.class)) {
//			if (block.getChunkValue().equals("true")) {
//				System.out.println(selectCovered(inputCas, Token.class, block).stream().map(Token::getText).collect(Collectors.joining("")));
//			}
//		}
//
//		System.out.println();
//		System.out.flush();
		
		for (Chunk block : select(inputCas, Chunk.class)) {
			System.out.printf("<Block valid:%s length:%d></Block>\n", block.getChunkValue(), block.getEnd() - block.getBegin());
//			for (Paragraph paragraph : selectCovered(inputCas, Paragraph.class, block)) {
//				System.out.printf("<Paragraph length:%d>\n", paragraph.getEnd() - paragraph.getBegin());
//				for (Sentence line : selectCovered(inputCas, Sentence.class, paragraph)) {
//					System.out.printf("<Line length:%d>\n", line.getEnd() - line.getBegin());
//			for (Token token : selectCovered(inputCas, Token.class, block)) {
//				printToken(inputCas, token);
//			}
//					System.out.println("</Line>");
//					System.out.flush();
//				}
//				System.out.println("</Paragraph>");
//				System.out.flush();
//			}
//			System.out.println("</Block>");
//			System.out.flush();
		}
	}
	
	private void printToken(JCas inputCas, Token token) {
		List<Anomaly> anomalies = selectCovered(inputCas, Anomaly.class, token);
		List<SpellingAnomaly> spellingAnomalies = selectCovered(inputCas, SpellingAnomaly.class, token);
		
		System.out.printf("%s", token.getText());
		
		if (!anomalies.isEmpty()) {
			System.out.printf("\tAnomaly<%s>", anomalies.stream().map(Anomaly::getDescription).collect(Collectors.joining(";")));
		}
		if (printAnnotations && spellingAnomalies.isEmpty() && anomalies.isEmpty()) {
			List<NamedEntity> nes = selectCovered(inputCas, NamedEntity.class, token);
			if (!nes.isEmpty()) {
				System.out.printf("\tAnnotation<%s>", nes.stream().map(NamedEntity::getValue).collect(Collectors.joining(";")));
			}
		}
		System.out.println();
		System.out.flush();
	}
}
