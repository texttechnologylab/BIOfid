package BIOfid.OCR;

import BIOfid.AbstractRunner;
import BIOfid.Utility.Util;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.SuggestedAction;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.cas.StringList;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.jetbrains.annotations.NotNull;
import org.texttechnologylab.annotation.ocr.*;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.*;

public abstract class AbstractOCRParser extends AbstractRunner {
	
	static AtomicInteger currentProgress = new AtomicInteger(0);
	static AtomicInteger maxProgress = new AtomicInteger(0);
	static long lastProgressPrint = 0L;
	
	protected static void processDocumentPathList(String sOutputPath, String sVocabularyPath, String sRawPath, String documentId, ArrayList<String> pathList) throws UIMAException {
		processDocumentPathList(sOutputPath, sVocabularyPath, sRawPath, documentId, pathList, false, null, null);
		
		AnalysisEngineDescription documentParser = createEngineDescription(CollectionProcessEngine.class,
				CollectionProcessEngine.INPUT_PATHS, pathList.toArray(new String[0]),
				CollectionProcessEngine.COLLECTION_ROOT_DIR, "",
				CollectionProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 75,
				CollectionProcessEngine.PARAM_BLOCK_TOP_MIN, 0,
				CollectionProcessEngine.PARAM_DICT_PATH, sVocabularyPath,
				CollectionProcessEngine.PARAM_MULTI_DOC, false);
		
		JCas jCas = JCasFactory.createJCas();
		
		DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
		documentMetaData.setDocumentId(documentId);
		
		runPipline(jCas, documentParser, documentId, sOutputPath, sRawPath);
	}
	
	protected static void processDocumentPathList(String sOutputPath, String sVocabularyPath, String sRawPath, String collectionId,
	                                              ArrayList<String> pathList, boolean bMultiDoc,
	                                              @Nullable File collectionRootDir, @Nullable String sArticleOutputPath) throws UIMAException {
		AbstractOCRParser.maxProgress.addAndGet(pathList.size());
		
		AnalysisEngineDescription documentParser = createEngineDescription(CollectionProcessEngine.class,
				CollectionProcessEngine.INPUT_PATHS, pathList.toArray(new String[0]),
				CollectionProcessEngine.COLLECTION_ROOT_DIR, Objects.nonNull(collectionRootDir) ? collectionRootDir.toString() : "",
				CollectionProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 75,
				CollectionProcessEngine.PARAM_BLOCK_TOP_MIN, 0,
				CollectionProcessEngine.PARAM_DICT_PATH, sVocabularyPath,
				CollectionProcessEngine.PARAM_MULTI_DOC, bMultiDoc);
		
		JCas jCas = JCasFactory.createJCas();
		
		DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
		documentMetaData.setCollectionId(collectionId);
		documentMetaData.setDocumentId(collectionId);
		
		runPipline(jCas, documentParser, collectionId, sOutputPath, sRawPath);
		
		if (sArticleOutputPath != null) {
			ArrayList<OCRDocument> ocrDocuments = new ArrayList<>(select(jCas, OCRDocument.class));
			
			ocrDocuments.removeAll(indexCovering(jCas, OCRDocument.class, OCRDocument.class).keySet());
			
			
			ocrDocuments.forEach(article -> exportArticleXmi(article, jCas, collectionId, sArticleOutputPath));
		}
		
		AbstractOCRParser.maxProgress.addAndGet(-1 * pathList.size());
	}
	
	/**
	 * Creates a new JCas for the article given by the article parameter.
	 * <p>
	 * The new JCas will carry all {@link Annotation Annotations} in the originating JCas covered by the article.
	 * The articles collectionId equals the originating collectionId, the documentId equals {@code article.getDocumentname()}.
	 * This cas is saved as XMI to the path given by sArticleOutputPath.
	 *
	 * @param article
	 * @param jCas
	 * @param collectionId
	 * @param sArticleOutputPath
	 */
	private static void exportArticleXmi(OCRDocument article, JCas jCas, String collectionId, @NotNull String sArticleOutputPath) {
		try (FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(sArticleOutputPath, article.getDocumentname() + ".xmi").toFile())) {
			JCas articleCas = JCasFactory.createJCas();
			
			articleCas.setDocumentText(article.getCoveredText());
			
			OCRDocument ocrDocument = new OCRDocument(articleCas, 0, article.getEnd() - article.getBegin());
			ocrDocument.setDocumentname(article.getDocumentname());
			articleCas.addFsToIndexes(ocrDocument);
			
			int articleBegin = article.getBegin();
			
			for (OCRPage covered : selectCovered(jCas, OCRPage.class, article)) {
				OCRPage ocrPage = new OCRPage(articleCas, covered.getBegin() - articleBegin, covered.getEnd() - articleBegin);
				ocrPage.setWidth(covered.getWidth());
				ocrPage.setHeight(covered.getHeight());
				ocrPage.setResolution(covered.getResolution());
				ocrPage.setPageId(covered.getPageId());
				ocrPage.setPageNumber(covered.getPageNumber());
				
				articleCas.addFsToIndexes(ocrPage);
			}
			
			for (OCRBlock covered : selectCovered(jCas, OCRBlock.class, article)) {
				OCRBlock ocrBlock = new OCRBlock(articleCas, covered.getBegin() - articleBegin, covered.getEnd() - articleBegin);
				ocrBlock.setTop(covered.getTop());
				ocrBlock.setBottom(covered.getBottom());
				ocrBlock.setLeft(covered.getLeft());
				ocrBlock.setRight(covered.getRight());
				ocrBlock.setBlockType(covered.getBlockType());
				ocrBlock.setBlockName(covered.getBlockName());
				ocrBlock.setValid(covered.getValid());
				
				articleCas.addFsToIndexes(ocrBlock);
			}
			
			for (OCRParagraph covered : selectCovered(jCas, OCRParagraph.class, article)) {
				OCRParagraph ocrParagraph = new OCRParagraph(articleCas, covered.getBegin() - articleBegin, covered.getEnd() - articleBegin);
				ocrParagraph.setLeftIndent(covered.getLeftIndent());
				ocrParagraph.setRightIndent(covered.getRightIndent());
				ocrParagraph.setStartIndent(covered.getStartIndent());
				ocrParagraph.setLineSpacing(covered.getLineSpacing());
				ocrParagraph.setAlign(covered.getAlign());
				
				articleCas.addFsToIndexes(ocrParagraph);
			}
			
			for (OCRLine covered : selectCovered(jCas, OCRLine.class, article)) {
				OCRLine ocrLine = new OCRLine(articleCas, covered.getBegin() - articleBegin, covered.getEnd() - articleBegin);
				ocrLine.setBaseline(covered.getBaseline());
				ocrLine.setTop(covered.getTop());
				ocrLine.setBottom(covered.getBottom());
				ocrLine.setLeft(covered.getLeft());
				ocrLine.setRight(covered.getRight());
				articleCas.addFsToIndexes(ocrLine);
			}
			
			for (OCRToken covered : selectCovered(jCas, OCRToken.class, article)) {
				OCRToken ocrToken = new OCRToken(articleCas, covered.getBegin() - articleBegin, covered.getEnd() - articleBegin);
				StringList stringList = new StringList(articleCas);
				covered.getSubTokenList().forEach(stringList::push);
				ocrToken.setSubTokenList(stringList);
				ocrToken.setIsWordFromDictionary(covered.getIsWordFromDictionary());
				ocrToken.setIsWordNormal(covered.getIsWordNormal());
				ocrToken.setIsWordNumeric(covered.getIsWordNumeric());
				ocrToken.setSuspiciousChars(covered.getSuspiciousChars());
				ocrToken.setContainsHyphen(covered.getContainsHyphen());
				
				articleCas.addFsToIndexes(ocrToken);
			}
			
			for (Anomaly covered : selectCovered(jCas, Anomaly.class, article)) {
				Anomaly anomaly = new Anomaly(articleCas, covered.getBegin() - articleBegin, covered.getEnd() - articleBegin);
				anomaly.setCategory(covered.getCategory());
				anomaly.setDescription(covered.getDescription());
				SuggestedAction suggestedAction = new SuggestedAction(articleCas);
				suggestedAction.setReplacement(covered.getSuggestions(0).getReplacement());
				FSArray fsArray = new FSArray(articleCas, 1);
				fsArray.set(0, suggestedAction);
				anomaly.setSuggestions(fsArray);
				
				articleCas.addFsToIndexes(anomaly);
			}
			
			DocumentMetaData articleMetadata = DocumentMetaData.create(articleCas);
			articleMetadata.setCollectionId(collectionId);
			articleMetadata.setDocumentId(article.getDocumentname());
			
			XmiCasSerializer.serialize(articleCas.getCas(), fileOutputStream);
		} catch (SAXException | IOException | UIMAException | StringIndexOutOfBoundsException e) {
			System.err.printf("Failed serialization of XMI for article %s!\n", article.getDocumentname());
			e.printStackTrace();
		}
	}
	
	private static void runPipline(JCas jCas, AnalysisEngineDescription documentParser, String collectionId, String
			sOutputPath, String sRawPath) throws AnalysisEngineProcessException, ResourceInitializationException {
		SimplePipeline.runPipeline(jCas, documentParser);
		
		try (FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(sOutputPath, collectionId + ".xmi").toFile())) {
			XmiCasSerializer.serialize(jCas.getCas(), fileOutputStream);
//						System.out.printf("\r%d/%d Wrote document %s.xmi", count, metadata.size(), collectionId);
		} catch (SAXException | IOException e) {
			System.err.printf("Failed serialization of XMI for document %s!\n", collectionId);
			e.printStackTrace();
		}
		
		if (!sRawPath.isEmpty()) {
			try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(sRawPath, collectionId + ".txt")), StandardCharsets.UTF_8))) {
				printWriter.print(getValidText(jCas));
			} catch (IOException e) {
				System.err.printf("Failed serialization of plain text for document %s!\n", collectionId);
			}
			try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(sRawPath, collectionId + "_orig.txt")), StandardCharsets.UTF_8))) {
				printWriter.print(jCas.getDocumentText());
			} catch (IOException e) {
				System.err.printf("Failed serialization of raw text for document %s!\n", collectionId);
			}
		}
	}
	
	
	public static String getValidText(JCas jCas) {
//		ImmutableMap<OCRBlock, Collection<OCRToken>> blockCovered = ImmutableMap.copyOf(indexCovered(jCas, OCRBlock.class, OCRToken.class));
//
//		ImmutableSet<OCRToken> tokenCovering = ImmutableSet.copyOf(indexCovering(jCas, OCRToken.class, OCRToken.class).keySet());
//		ImmutableSet<OCRToken> anomalies = ImmutableSet.copyOf(indexCovered(jCas, Anomaly.class, OCRToken.class).values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
//
//		StringBuilder retStringBuilder = new StringBuilder();
//
//		for (OCRBlock ocrBlock : select(jCas, OCRBlock.class)) {
//			if (ocrBlock.getValid()) {
//				if (!blockCovered.containsKey(ocrBlock) || blockCovered.get(ocrBlock) == null || blockCovered.get(ocrBlock).isEmpty())
//					continue;
//				for (OCRToken ocrToken : blockCovered.get(ocrBlock)) {
//					if (tokenCovering.contains(ocrToken)) continue;
////					if (!ocrToken.getCoveredText().equals(" ") && (anomalies.contains(ocrToken) || tokenCovering.contains(ocrToken))) continue;
//					retStringBuilder.append(ocrToken.getCoveredText()).append(" ");
//				}
//			}
//		}

//		StringBuilder debugStringBuilder = new StringBuilder();
//		for (OCRBlock ocrBlock : select(jCas, OCRBlock.class)) {
//			debugStringBuilder.append(String.format("<OCRBlock valid:%b, type:%s, top:%d, bottom:%d>\n", ocrBlock.getValid(), ocrBlock.getBlockType(), ocrBlock.getTop(), ocrBlock.getBottom()));
//			if (!blockCovered.containsKey(ocrBlock) || blockCovered.get(ocrBlock) == null || blockCovered.get(ocrBlock).isEmpty())
//				continue;
//			for (OCRToken ocrToken : blockCovered.get(ocrBlock)) {
//				if (tokenCovering.contains(ocrToken)) continue;
////					if (!ocrToken.getCoveredText().equals(" ") && (anomalies.contains(ocrToken) || tokenCovering.contains(ocrToken))) continue;
//				debugStringBuilder.append(ocrToken.getCoveredText());
//			}
//			debugStringBuilder.append("\n</OCRBlock>\n");
//		}
//		System.out.println(debugStringBuilder.toString());
		ArrayList<OCRDocument> ocrDocuments = new ArrayList<>(select(jCas, OCRDocument.class));
		ocrDocuments.removeAll(indexCovering(jCas, OCRDocument.class, OCRDocument.class).keySet());
		
		ImmutableMap<OCRDocument, Collection<OCRToken>> documentCoveringToken = ImmutableMap.copyOf(indexCovered(jCas, OCRDocument.class, OCRToken.class));
		
		ImmutableSet<OCRToken> tokenCovering = ImmutableSet.copyOf(indexCovering(jCas, OCRToken.class, OCRToken.class).keySet());
		ImmutableSet<OCRToken> anomalies = ImmutableSet.copyOf(indexCovered(jCas, Anomaly.class, OCRToken.class).entrySet().stream()
				.filter(entry -> entry.getKey().getCategory().equals("BioFID_Garbage_Line_Anomaly"))
				.map(Map.Entry::getValue)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet()));
		
		StringBuilder retStringBuilder = new StringBuilder();

//		    ArrayList<String> skipped = new ArrayList<>();
		
		for (OCRDocument ocrDocument : ocrDocuments) {
			try {
				if (!documentCoveringToken.containsKey(ocrDocument) || ocrDocument.getCoveredText().isEmpty())
					continue;
				Util.processDocument(ocrDocument, documentCoveringToken, tokenCovering, anomalies, retStringBuilder);
			} catch (StringIndexOutOfBoundsException e) {
				e.printStackTrace();
			}
		}
//		System.out.println(select(jCas, OCRLine.class).stream().map(OCRLine::getCoveredText).collect(Collectors.joining("\n")));
//
//		System.out.println();
//		System.out.println();
//		System.out.println();
		
		return retStringBuilder.toString();
	}
	
	public static void printProgress(boolean force) {
		long now = System.currentTimeMillis();
		boolean diff = (now - lastProgressPrint) > 500;
		if (force || diff || currentProgress.get() == maxProgress.get()) {
			lastProgressPrint = now;
			System.out.printf("\rCurrently processed/queued: %d/%d", currentProgress.get(), maxProgress.get());
			System.out.flush(); // FIXME: System.out.flush() needed?
		}
	}
}
