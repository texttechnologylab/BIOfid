import BIOfid.Extraction.ConllBIO2003Writer;
import BIOfid.Extraction.HierarchicalBioEncoder;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Location;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Organization;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.Person;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.conll.Conll2003Writer;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

public class ConllBIO2003WriterTest {
	
	@Test
	public void testEncoder() {
		try {
			JCas jCas = getjCas();
			
			HierarchicalBioEncoder encoder = new HierarchicalBioEncoder(jCas, false);
			
			for (Token token : JCasUtil.select(jCas, Token.class)) {
				System.out.printf("%s %s\n", token.getCoveredText(), encoder.getTags(token, 0));
			}
			System.out.println();
			for (Token token : JCasUtil.select(jCas, Token.class)) {
				System.out.printf("%s %s\n", token.getCoveredText(), encoder.getTags(token, 1));
			}
			System.out.println();
			for (Token token : JCasUtil.select(jCas, Token.class)) {
				System.out.printf("%s %s\n", token.getCoveredText(), encoder.getTags(token, 2));
			}
			System.out.println();
			for (Token token : JCasUtil.select(jCas, Token.class)) {
				System.out.printf("%s %s\n", token.getCoveredText(), encoder.getTags(token, 3));
			}
			System.out.println();
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void conllWriter() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("basic");
			documentMetaData.setDocumentUri("basicGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					Conll2003Writer.class,
					Conll2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					Conll2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	@DisplayName("Single Column Test")
	public void singleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("singleColumn");
			documentMetaData.setDocumentUri("singleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void exampleFile() {
		try {
			JCas jCas = JCasFactory.createJCas();
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 1,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true);
			
			CasIOUtil.readXmi(jCas, new File("src/test/resources/de_9032686_1925_Marx, Arno.xmi"));
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("de_9032686_1925");
			documentMetaData.setDocumentUri("de_9032686_1925");
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	@DisplayName("Double Column Test")
	public void doubleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("doubleColumn");
			documentMetaData.setDocumentUri("doubleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_NAMED_ENTITY_COLUMNS, 2,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	@DisplayName("Triple Column Test")
	public void tripleColumn() {
		try {
			JCas jCas = getjCas();
			
			// Metadata
			DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
			documentMetaData.setDocumentId("tripleColumn");
			documentMetaData.setDocumentUri("tripleColumnGUF");
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "src/test/out/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_NAMED_ENTITY_COLUMNS, 3,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 1,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false);
			
			conllEngine.process(jCas);
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	
	@NotNull
	private JCas getjCas() throws UIMAException {
		JCas jCas = JCasFactory.createJCas();
		jCas.setDocumentText("Goethe Universität Frankfurt am Main");
		
		jCas.addFsToIndexes(new Sentence(jCas, 0, 36));
		jCas.addFsToIndexes(new Token(jCas, 0, 6));
		jCas.addFsToIndexes(new Token(jCas, 7, 18));
		jCas.addFsToIndexes(new Token(jCas, 19, 28));
		jCas.addFsToIndexes(new Token(jCas, 29, 31));
		jCas.addFsToIndexes(new Token(jCas, 32, 36));
		
		// Goethe Universität Frankfurt
		Organization org = new Organization(jCas, 0, 28);
		org.setValue("ORG");
		jCas.addFsToIndexes(org);
		
		// Goethe Universität
		Organization org2 = new Organization(jCas, 0, 18);
		org2.setValue("ORG");
		jCas.addFsToIndexes(org2);

//		// Universität Frankfurt
		Organization org3 = new Organization(jCas, 7, 28);
		org3.setValue("ORG");
		jCas.addFsToIndexes(org3);
		
		// Goethe
		Person person = new Person(jCas, 0, 6);
		person.setValue("PER");
		jCas.addFsToIndexes(person);
		
		// Frankfurt
		Location location = new Location(jCas, 19, 28);
		location.setValue("LOC");
		jCas.addFsToIndexes(location);
		
		// Frankfurt am Main
		Location location2 = new Location(jCas, 19, 36);
		location2.setValue("LOC");
		jCas.addFsToIndexes(location2);

//		// Main
		Location location3 = new Location(jCas, 32, 36);
		location3.setValue("LOC");
		jCas.addFsToIndexes(location3);
		
		return jCas;
	}
}