import BIOfid.OCR.CollectionProcessEngine;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.texttechnologylab.annotation.ocr.OCRBlock;
import org.texttechnologylab.annotation.ocr.OCRLine;
import org.texttechnologylab.annotation.ocr.OCRPage;
import org.texttechnologylab.annotation.ocr.OCRToken;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.*;

@DisplayName("CollectionProcessEngine Test")
class CollectionProcessEngineTest {
	
	//	static final ImmutableList<String> documentIds = ImmutableList.of("9032259", "9031469", "9031472", "9031473", "9031474", "9031475", "9031476", "9031477", "9032261");
	static final ImmutableList<String> documentIds = ImmutableList.of("9699895", "9655813", "9655814", "9655815", "9655816", "9655817", "9655818", "9655819", "9655820", "9655821", "9655822", "9655823", "9655824", "9655825", "9655826", "9655827", "9655828", "9655829", "9655830", "9655831", "9655832", "9655833", "9655834", "9655835", "9655836", "9655837", "9655838", "9655839", "9655840", "9655841", "9655842", "9655843", "9655844");
	private static JCas jCas;
	
	@BeforeAll
	@DisplayName("Set Up")
	static void setUp() throws IOException, UIMAException {
		System.out.println("Getting JCas...");
		
		HashMap<String, String> fileAtlas = new HashMap<>();
		try (BufferedReader bufferedReader = Files.newReader(new File("/home/s3676959/Documents/BioFID/Export/file_atlas.txt"), StandardCharsets.UTF_8)) {
			bufferedReader.lines().map(l -> l.split("\\s")).forEach(arr -> fileAtlas.put(arr[0], arr[1]));
		}
		
		if (fileAtlas.isEmpty()) {
			throw new NullPointerException("Files could not be found!");
		}
		System.out.printf("Atlas size %d.\n", fileAtlas.size());
		
		ArrayList<String> pathList = new ArrayList<>();
		for (String documentId : documentIds) {
			String path = fileAtlas.getOrDefault(documentId, null);
			if (path != null && new File(path).isFile()) pathList.add(path);
		}
		
		System.out.println(pathList);
		
		AnalysisEngineDescription documentParser = createEngineDescription(CollectionProcessEngine.class,
				CollectionProcessEngine.INPUT_PATHS, pathList.toArray(new String[0]),
				CollectionProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 90,
				CollectionProcessEngine.PARAM_DICT_PATH, "src/test/resources/Leipzig40MT2010_lowered.5.vocab");
		
		JCas inputCas = JCasFactory.createJCas();
		
		// Pipeline
		SimplePipeline.runPipeline(inputCas, documentParser);
		
		jCas = inputCas;
		
	}
	
	@Test
	@DisplayName("Test Serializing")
	void testSerializing() {
		try (FileOutputStream fileOutputStream = new FileOutputStream(new File("src/test/" + documentIds.get(0) + ".xmi"))) {
			XmiCasSerializer.serialize(jCas.getCas(), fileOutputStream);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
			assert false;
		}
	}
	
	@Nested
	@DisplayName("With parsed Document")
	class WithDocument {
		@Test
		@DisplayName("Test Pages")
		void testPages() {
			OCRPage ocrPage = select(jCas, OCRPage.class).stream().findAny().get();
			String tokens = selectCovered(jCas, OCRToken.class, ocrPage).stream().map(OCRToken::getCoveredText).collect(Collectors.joining(" "));
			System.out.printf("<OCRPage number:%d, id:%s, begin:%d, end:%d>%s</OCRPage>\n",
					ocrPage.getPageNumber(), ocrPage.getPageId(), ocrPage.getBegin(), ocrPage.getEnd(), tokens);
		}
		
		@Test
		@DisplayName("Test Blocks")
		void testBlocks() {
			Map<OCRBlock, Collection<OCRToken>> blockCovered = indexCovered(jCas, OCRBlock.class, OCRToken.class);
			blockCovered.entrySet().stream().limit(5).forEachOrdered(entry -> {
				OCRBlock ocrBlock = entry.getKey();
				String tokens = entry.getValue().stream().map(Annotation::getCoveredText).collect(Collectors.joining(" "));
				System.out.printf("<OCRBlock valid:%b, begin:%d, end:%d>%s</OCRBlock>\n",
						ocrBlock.getValid(), ocrBlock.getBegin(), ocrBlock.getEnd(), tokens);
			});
		}
		
		@Test
		@DisplayName("Test Covered")
		void testCovered() {
			Map<OCRToken, Collection<OCRToken>> tokenCovering = indexCovered(jCas, OCRToken.class, OCRToken.class);
			tokenCovering.entrySet().stream().filter(entry -> entry.getValue().size() > 1).limit(5).forEachOrdered(entry ->
					System.out.printf("<OCRToken>%s<Subtoken:['%s']/></OCRToken>\n",
							entry.getKey().getCoveredText(),
							entry.getValue().stream()
									.filter(o -> !entry.getKey().equals(o))
									.map(Annotation::getCoveredText)
									.collect(Collectors.joining("','"))));
		}
		
		@Test
		@DisplayName("Test Lines")
		void testLines() {
			Map<OCRLine, Collection<OCRToken>> linesCovered = indexCovered(jCas, OCRLine.class, OCRToken.class);
			select(jCas, OCRLine.class).stream().limit(5).forEachOrdered(line -> System.out.printf("<OCRLine left:%d, top:%d>%s</OCRLine>\n",
					line.getLeft(), line.getTop(), linesCovered.get(line).stream().map(Annotation::getCoveredText).collect(Collectors.joining(" "))));
		}
	}
}
