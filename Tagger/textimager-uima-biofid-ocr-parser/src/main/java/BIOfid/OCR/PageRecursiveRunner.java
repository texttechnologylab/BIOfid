package BIOfid.OCR;

import com.google.common.collect.Streams;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.chunk.Chunk;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

public class PageRecursiveRunner {
	
	public static void main(String[] args) {
		String basePath = "~/Documents/Biodiversität_OCR_Lieferung_1/9031458/";
		String outPath = "~/Documents/out/9031458/";

//		boolean keepFolderStructure = true;
		Stream<File> files = Streams.stream(Files.fileTraverser().depthFirstPostOrder(new File(basePath)));
		System.out.printf("Traversing %d elements..\n\n\n", files.count());
		System.out.flush();
		files.forEachOrdered(file -> {
			try {
				if (file.isDirectory())
					return;
				Path relativePath = Paths.get(file.getAbsolutePath().substring(basePath.length()));
				Path finalPath = Paths.get(outPath, relativePath.toString().replaceAll("\\.xml", ".txt"));
				finalPath.getParent().toFile().mkdirs();
				
				String content = exampleOutput(file);
				writeToFile(finalPath.toFile(), content);
			} catch (UIMAException e) {
				e.printStackTrace();
			}
		});
	}
	
	private static String exampleOutput(File file) throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			xml = br.lines().collect(Collectors.joining("\n"));
			
			// Create a new Engine Description.
			AnalysisEngineDescription pageParser = createEngineDescription(PageProcessEngine.class,
					PageProcessEngine.INPUT_XML, xml,
					PageProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 90,
					PageProcessEngine.PARAM_BLOCK_TOP_MIN, 400,
					PageProcessEngine.PARAM_DICT_PATH, "~/Documents/BIOfid/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/resources/Leipzig40MT2010_lowered.5.vocab");
			
			// Create a new JCas - "Holder"-Class for Annotation.
			JCas inputCas = JCasFactory.createJCas();
			
			// Pipeline
			SimplePipeline.runPipeline(inputCas, pageParser);
			
			final StringBuilder finalText = new StringBuilder();
			
			final int[] tokenCount = {0};
			for (Chunk block : select(inputCas, Chunk.class)) {
				if (block.getChunkValue().equals("true")) {
					selectCovered(inputCas, Token.class, block).stream().map(Token::getText).forEachOrdered(str ->
					{
						finalText.append(str);
						if (!str.equals(" ")) tokenCount[0]++;
					});
				}
			}
			System.out.printf("File '%s' length: %d, token count:%d\n", file.getName().replaceAll("\\.xml", ".txt"), xml.length(), tokenCount[0]);
			System.out.flush();
			return finalText.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	static private void writeToFile(File targetFile, String content) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(targetFile))) {
			bw.write(content);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
