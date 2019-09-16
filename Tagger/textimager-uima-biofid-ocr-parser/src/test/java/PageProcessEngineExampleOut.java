import BIOfid.OCR.PageProcessEngine;
import com.google.common.base.Strings;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.chunk.Chunk;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.junit.Test;

import java.io.*;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;

import static BIOfid.Utility.Util.writeToFile;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

/**
 * Created on 21.01.2019.
 */
public class PageProcessEngineExampleOut {
	@Test
	public void runAllTest() {
		String path = "src/test/out/Biodiversity/9088917/9088369";
		String pathname = "src/test/resources/Biodiversity/9088917/9088369";
		run(path, pathname);
	}

	@Test
	public void runExternal() {
		String path = "src/test/out/4704355";
		String pathname = "/home/s3676959/Documents/BIOfid/Export/Botanische_Zeitschriften//4704355";
		run(path, pathname);
	}

	private void run(String path, String pathname) {
		try {
			new File(path).mkdirs();
			for (File folder : Objects.requireNonNull(new File(pathname).listFiles())) {
				if (folder.isFile()) {
					String content = exampleOutput(folder);
					if (Strings.isNullOrEmpty(content))
						continue;
					writeToFile(Paths.get(path, folder.getName().replaceAll("\\.xml", ".txt")), content);
				} else {
					Paths.get(path, folder.getName()).toFile().mkdirs();
					for (File file : Objects.requireNonNull(folder.listFiles())) {
						String content = exampleOutput(file);
						if (Strings.isNullOrEmpty(content))
							continue;
						writeToFile(Paths.get(path, folder.getName(), file.getName().replaceAll("\\.xml", ".txt")), content);
					}
				}
			}
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}

	private String exampleOutput(File file) throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			xml = br.lines().collect(Collectors.joining("\n"));

			// Create a new Engine Description.
			AnalysisEngineDescription pageParser = createEngineDescription(PageProcessEngine.class,
					PageProcessEngine.INPUT_XML, xml,
					PageProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 90,
					PageProcessEngine.PARAM_DICT_PATH, "src/test/resources/Leipzig40MT2010_lowered.5.vocab");

			// Create a new JCas - "Holder"-Class for Annotation.
			JCas inputCas = JCasFactory.createJCas();

			// Pipeline
			SimplePipeline.runPipeline(inputCas, pageParser);

			StringBuilder finalText = new StringBuilder();

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
}
