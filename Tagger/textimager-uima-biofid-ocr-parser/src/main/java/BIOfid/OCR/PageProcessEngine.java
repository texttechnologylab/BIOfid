package BIOfid.OCR;

import BIOfid.OCR.Annotation.*;
import BIOfid.OCR.Annotation.Paragraph;
import BIOfid.OCR.Annotation.Token;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.SpellingAnomaly;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.chunk.Chunk;
import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.languagetool.JLanguageTool;
import org.languagetool.rules.RuleMatch;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PageProcessEngine extends SegmenterBase
{
	
	public static final String INPUT_XML = "pInputPaths";
	public static final String PARAM_DICT_PATH = "pDictPath";
	public static final String PARAM_MIN_TOKEN_CONFIDENCE = "pMinTokenConfidence";
	public static final String PARAM_USE_LANGUAGE_TOOL = "pUseLanguageTool";
	public static final String PARAM_CHAR_LEFT_MAX = "pCharLeftMax";
	public static final String PARAM_BLOCK_TOP_MIN = "pBlockTopMin";
	@ConfigurationParameter(name = INPUT_XML, mandatory = true)
	protected String pInputXML;
	@ConfigurationParameter(name = PARAM_DICT_PATH, mandatory = false)
	protected String pDictPath;
	@ConfigurationParameter(name = PARAM_MIN_TOKEN_CONFIDENCE, mandatory = false, defaultValue = "80")
	protected Integer pMinTokenConfidence;
	@ConfigurationParameter(name = PARAM_USE_LANGUAGE_TOOL, mandatory = false, defaultValue = "false")
	protected Boolean pUseLanguageTool;
	@ConfigurationParameter(name = PARAM_CHAR_LEFT_MAX, mandatory = false, defaultValue = "99999")
	protected Integer pCharLeftMax;
	@ConfigurationParameter(name = PARAM_BLOCK_TOP_MIN, mandatory = false, defaultValue = "300")
	protected Integer pBlockTopMin;

	HashSet<String> dict;

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException
	{

		try {
			loadDict();
			JLanguageTool langTool = new JLanguageTool(new org.languagetool.language.GermanyGerman());

			SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
			SAXParser saxParser = saxParserFactory.newSAXParser();
			FineReaderExportHandler fineReaderExportHandler = new FineReaderExportHandler();
			fineReaderExportHandler.charLeftMax = pCharLeftMax;
			fineReaderExportHandler.blockTopMin = pBlockTopMin;
			InputStream inputStream = IOUtils.toInputStream(pInputXML, Charsets.UTF_8);
			saxParser.parse(inputStream, fineReaderExportHandler);
			
			String text = fineReaderExportHandler.tokens.stream().map(Token::getTokenString).collect(Collectors.joining(""));
			aJCas.setDocumentText(text);
			
			for (Block OCRBlock : fineReaderExportHandler.blocks) {
				Chunk chunk = new Chunk(aJCas, OCRBlock.start, OCRBlock.end);
				chunk.setChunkValue(OCRBlock.valid ? "true" : "false");
				aJCas.addFsToIndexes(chunk);
			}
			
			for (Paragraph par : fineReaderExportHandler.paragraphs) {
				aJCas.addFsToIndexes(new de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Paragraph(aJCas, par.start, par.end));
			}
			
			for (Line OCRLine : fineReaderExportHandler.lines) {
				aJCas.addFsToIndexes(new Sentence(aJCas, OCRLine.start, OCRLine.end));
			}
			
			for (Token OCRToken : fineReaderExportHandler.tokens) {
				aJCas.addFsToIndexes(new de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token(aJCas, OCRToken.start, OCRToken.end));
			}
			
			for (Token OCRToken : fineReaderExportHandler.tokens) {
				if (OCRToken.isSpace())
					continue;
				boolean inDict = inDict(OCRToken.getTokenString());
				if (!inDict && (OCRToken.getAverageCharConfidence() < pMinTokenConfidence || !(OCRToken.isWordNormal || OCRToken.isWordFromDictionary || OCRToken.isWordNumeric))) {
					Anomaly anomaly = new Anomaly(aJCas, OCRToken.start, OCRToken.end);
					anomaly.setDescription(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, inDict:%b, isWordNumeric:%b, suspiciousChars:%d",
							OCRToken.getAverageCharConfidence(), OCRToken.isWordNormal, OCRToken.isWordFromDictionary, inDict, OCRToken.isWordNumeric, OCRToken.suspiciousChars));
					aJCas.addFsToIndexes(anomaly);
				} else if (OCRToken.containsHyphen() || OCRToken.subTokenStrings().size() > 1) {
					NamedEntity annotation = new NamedEntity(aJCas, OCRToken.start, OCRToken.end);
					annotation.setValue(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, inDict:%b, isWordNumeric:%b, suspiciousChars:%d, containsHyphen:%b, subTokens:%s",
							OCRToken.getAverageCharConfidence(), OCRToken.isWordNormal, OCRToken.isWordFromDictionary, inDict, OCRToken.isWordNumeric, OCRToken.suspiciousChars, OCRToken.containsHyphen(), OCRToken.subTokenStrings()));
					aJCas.addFsToIndexes(annotation);
				}
			}

			if (pUseLanguageTool) {
				List<RuleMatch> ruleMatches = langTool.check(text, false, JLanguageTool.ParagraphHandling.NORMAL);
				for (RuleMatch ruleMatch : ruleMatches) {
					SpellingAnomaly spellingAnomaly = new SpellingAnomaly(aJCas, ruleMatch.getFromPos(), ruleMatch.getToPos());
					spellingAnomaly.setDescription(String.format("Message:%s, SuggestedReplacements:%s",
							ruleMatch.getMessage(), ruleMatch.getSuggestedReplacements()));
					aJCas.addFsToIndexes(spellingAnomaly);
				}
			}

		} catch (SAXException | ParserConfigurationException | IOException e) {
			e.printStackTrace();
		}
	}

	void loadDict()
	{
		if (pDictPath != null) {
			try (BufferedReader br = new BufferedReader(new FileReader(new File(pDictPath)))) {
				dict = br.lines().collect(Collectors.toCollection(HashSet::new));
			} catch (Exception e) {
				getLogger().error("Dict could not be loaded!");
			}
		}
	}

	boolean inDict(String token)
	{
		Pattern pattern = Pattern.compile("[^-\\p{Alnum}]", Pattern.UNICODE_CHARACTER_CLASS);
		String word = pattern.matcher(token).replaceAll("").toLowerCase(); // FIXME: toLowerCase
		return dict != null && !word.isEmpty() && dict.contains(word);
	}

	@Override
	protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException
	{

	}
}
