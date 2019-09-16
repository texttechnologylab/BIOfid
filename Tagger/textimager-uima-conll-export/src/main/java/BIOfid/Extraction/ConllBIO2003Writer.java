package BIOfid.Extraction;

import com.google.common.base.Strings;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.CompressionUtils;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.io.conll.Conll2003Writer;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.*;

import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

public class ConllBIO2003Writer extends Conll2003Writer {

	/**
	 * Character encoding of the output data.
	 */

	private static final String UNUSED = "_";
	public static final String PARAM_TARGET_ENCODING = ComponentParameters.PARAM_TARGET_ENCODING;
	@ConfigurationParameter(name = PARAM_TARGET_ENCODING, mandatory = true, defaultValue = ComponentParameters.DEFAULT_ENCODING)
	private String targetEncoding;

	public static final String PARAM_FILENAME_EXTENSION = ComponentParameters.PARAM_FILENAME_EXTENSION;
	@ConfigurationParameter(name = PARAM_FILENAME_EXTENSION, mandatory = true, defaultValue = ".conll")
	private String filenameSuffix;

	public static final String PARAM_WRITE_POS = ComponentParameters.PARAM_WRITE_POS;
	@ConfigurationParameter(name = PARAM_WRITE_POS, mandatory = true, defaultValue = "true")
	private boolean writePos;

	public static final String PARAM_WRITE_CHUNK = ComponentParameters.PARAM_WRITE_CHUNK;
	@ConfigurationParameter(name = PARAM_WRITE_CHUNK, mandatory = true, defaultValue = "true")
	private boolean writeChunk;

	public static final String PARAM_WRITE_NAMED_ENTITY = ComponentParameters.PARAM_WRITE_NAMED_ENTITY;
	@ConfigurationParameter(name = PARAM_WRITE_NAMED_ENTITY, mandatory = true, defaultValue = "true")
	private boolean writeNamedEntity;

	/**
	 * The number of desired NE columns
	 */
	public static final String PARAM_NAMED_ENTITY_COLUMNS = "pNamedEntityColumns";
	@ConfigurationParameter(name = PARAM_NAMED_ENTITY_COLUMNS, defaultValue = "1")
	private Integer pNamedEntityColumns;

	public static final String PARAM_CONLL_SEPARATOR = "pConllSeparator";
	@ConfigurationParameter(name = PARAM_CONLL_SEPARATOR, defaultValue = " ")
	private String pConllSeparator;

	public static final String PARAM_STRATEGY_INDEX = "pEncoderStrategyIndex";
	@ConfigurationParameter(name = PARAM_STRATEGY_INDEX, defaultValue = "0")
	private Integer pEncoderStrategyIndex;

	public static final String PARAM_FILTER_FINGERPRINTED = "pFilterFingerprinted";
	@ConfigurationParameter(name = PARAM_FILTER_FINGERPRINTED, defaultValue = "true")
	private Boolean pFilterFingerprinted;

	/*
	 * Raw export parameters
	 */

	/**
	 * If true, the raw document text will also be exported.
	 */
	public static final String PARAM_EXPORT_RAW = "pExportRaw";
	@ConfigurationParameter(name = PARAM_EXPORT_RAW, mandatory = false, defaultValue = "false")
	private Boolean pExportRaw;

	/**
	 * If true, only the raw document text will be exported.
	 */
	public static final String PARAM_EXPORT_RAW_ONLY = "pExportRawOnly";
	@ConfigurationParameter(name = PARAM_EXPORT_RAW_ONLY, mandatory = false, defaultValue = "false")
	private Boolean pExportRawOnly;

	/**
	 * The target location for the raw document text.
	 */
	public static final String PARAM_RAW_TARGET_LOCATION = "pRawTargetLocation";
	@ConfigurationParameter(name = PARAM_RAW_TARGET_LOCATION, mandatory = false)
	private String pRawTargetLocation;

	/**
	 * Target location. If this parameter is not set, data is written to stdout.
	 */
	public static final String PARAM_RAW_FILENAME_SUFFIX = "pRawFilenameSuffix";
	@ConfigurationParameter(name = PARAM_RAW_FILENAME_SUFFIX, mandatory = false, defaultValue = ".txt")
	private String pRawFilenameSuffix;

	private Map<Token, Collection<NamedEntity>> tokenNamedEntityMap;
	private HashMap<Token, ArrayList<NamedEntity>> hierachialTokenNamedEntityMap;
	private HashMap<Token, ArrayList<String>> hierachialTokenBIONamedEntityMap;

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		if (!pExportRawOnly) {
			try (PrintWriter conllWriter = new PrintWriter(new OutputStreamWriter(getOutputStream(aJCas, filenameSuffix), targetEncoding))) {
				HierarchicalBioEncoder hierarchicalBioEncoder = new HierarchicalBioEncoder(aJCas, pFilterFingerprinted);

				for (Sentence sentence : select(aJCas, Sentence.class)) {
					HashMap<Token, Row> ctokens = new LinkedHashMap<>();

					// Tokens
					List<Token> tokens = selectCovered(Token.class, sentence);


					for (Token token : tokens) {
						Lemma lemma = token.getLemma();
						Row row = new Row();
						row.token = token;
						row.chunk = (lemma != null && !Strings.isNullOrEmpty(lemma.getValue())) ? lemma.getValue() : "--";
						row.ne = hierarchicalBioEncoder.getTags(token, pEncoderStrategyIndex);
						ctokens.put(row.token, row);
					}

					// Write sentence in CONLL 2006 format
					for (Row row : ctokens.values()) {
						String pos = UNUSED;
						if (writePos && (row.token.getPos() != null)) {
							POS posAnno = row.token.getPos();
							pos = posAnno.getPosValue();
						}

						String chunk = UNUSED;
						if (writeChunk && (row.chunk != null)) {
							chunk = row.chunk;
						}

						String namedEntities = UNUSED;
						if (writeNamedEntity && (row.ne != null)) {
							StringBuilder neBuilder = new StringBuilder();
							if (!row.ne.isEmpty()) {
								neBuilder.append(row.ne.get(0));
							} else {
								neBuilder.append("O");
							}
							for (int i = 1; i < pNamedEntityColumns; i++) {
								neBuilder.append(pConllSeparator);
								if (row.ne.size() > i) {
									neBuilder.append(row.ne.get(i));
								} else {
									neBuilder.append("O");
								}
							}
							namedEntities = neBuilder.toString();
						}

						conllWriter.printf("%s%s%s%s%s%s%s\n", row.token.getCoveredText(), pConllSeparator, pos, pConllSeparator, chunk, pConllSeparator, namedEntities);
					}
					conllWriter.println();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (pExportRaw || pExportRawOnly) {
			try (PrintWriter rawWriter = new PrintWriter(new OutputStreamWriter(getRawOutputStream(aJCas, pRawFilenameSuffix), targetEncoding))) {
				rawWriter.print(aJCas.getDocumentText());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static final class Row {
		Token token;
		String chunk;
		ArrayList<String> ne;
	}

	protected NamedOutputStream getRawOutputStream(JCas aJCas, String aExtension) throws IOException {
		if (pRawTargetLocation == null) {
			return new NamedOutputStream(null, new CloseShieldOutputStream(System.out));
		} else {
			return getRawOutputStream(getRelativePath(aJCas), aExtension);
		}
	}

	protected NamedOutputStream getRawOutputStream(String aRelativePath, String aExtension) throws IOException {
		File outputFile = new File(pRawTargetLocation, aRelativePath + aExtension);

//		if (!overwrite && outputFile.exists()) {
//			throw new IOException("Target file [" + outputFile
//					+ "] already exists and overwriting not enabled.");
//		}

		return new NamedOutputStream(outputFile.getAbsolutePath(),
				CompressionUtils.getOutputStream(outputFile));
	}


}
