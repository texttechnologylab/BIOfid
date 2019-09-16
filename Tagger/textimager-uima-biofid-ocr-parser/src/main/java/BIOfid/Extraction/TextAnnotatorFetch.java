package BIOfid.Extraction;

import BIOfid.AbstractRunner;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.http.client.HttpResponseException;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.CasCopier;
import org.apache.uima.util.CasIOUtils;
import org.hucompute.utilities.helper.RESTUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import static BIOfid.Utility.Util.writeToFile;

public class TextAnnotatorFetch extends AbstractRunner {

	private static final String textannotator = "http://141.2.108.253:50555/";
	private static final String mongoDB = "https://resources.hucompute.org/mongo/";

	private static String sRepository = "14393";
	private static String sSession;
	private static String conllLocation;
	private static String XMILocation;
	private static String textLocation;

	public static void main(String[] args) {
		try {
			getParams(args);

			int index;
			index = Integer.max(params.indexOf("-s"), params.indexOf("--session"));
			if (index > -1) {
				sSession = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --session!\n");
			}

			index = params.indexOf("--xmi");
			if (index > -1) {
				XMILocation = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --xmi!\n");
			}

			index = params.indexOf("--conll");
			if (index > -1) {
				conllLocation = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --conll!\n");
			}

			index = params.indexOf("--text");
			if (index > -1) {
				textLocation = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --text!\n");
			}

			index = params.indexOf("--threads");
			final int pThreads = index > -1 ? Integer.parseInt(params.get(index + 1)) : 4;

			final boolean forceUTF8 = params.indexOf("--forceUTF8") > -1;
			final boolean reserialize = forceUTF8 & params.indexOf("--reserialize") > -1;

			index = params.indexOf("--strategyIndex");
			final int strategyIndex = (index > -1) ? Integer.parseInt(params.get(index)) : 0;

			index = params.indexOf("--repository");
			if (index > -1) {
				sRepository = params.get(index + 1);
			}

			String requestURL = textannotator + "documents/" + sRepository;
			final JSONObject remoteFiles = RESTUtils.getObjectFromRest(requestURL, sSession);

			if (remoteFiles.getBoolean("success")) {
				final JSONArray rArray = remoteFiles.getJSONArray("result");

				try {
					final ForkJoinPool remotePool = new ForkJoinPool(pThreads);

					System.out.printf("Running TextAnnotatorFetch in parallel with %d threads for %d files\n", remotePool.getParallelism(), rArray.length());

					final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
							ConllBIO2003Writer.class,
							ConllBIO2003Writer.PARAM_TARGET_LOCATION, conllLocation,
							ConllBIO2003Writer.PARAM_STRATEGY_INDEX, strategyIndex,
							ConllBIO2003Writer.PARAM_OVERWRITE, true,
							ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true);

					int[] count = {0};
					remotePool.submit(() -> IntStream.range(0, rArray.length()).parallel().forEach(a -> {
						try {
							String documentURI = mongoDB + rArray.get(a).toString();
							JSONObject documentJSON = RESTUtils.getObjectFromRest(documentURI, sSession);

							if (documentJSON.getBoolean("success")) {
								String documentName = documentJSON.getJSONObject("result").getString("name");
								String cleanDocumentName = documentName.replaceFirst("[^_]*_(\\d+)_.*", "$1");

								// Download XMI
								URL casURL = new URL(textannotator + "cas/" + rArray.get(a).toString() + "?session=" + sSession);

								// Process XMI & write conll
								JCas jCas = JCasFactory.createJCas();

								File utf8File = Paths.get(XMILocation, cleanDocumentName + ".xmi").toFile();
								if (forceUTF8) {
									PrintWriter printWriter = new PrintWriter(Files.newWriter(utf8File, StandardCharsets.UTF_8));
									BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(casURL.openStream()));
									bufferedReader.lines().forEachOrdered(printWriter::println);
									bufferedReader.close();
									printWriter.close();
									CasIOUtil.readXmi(jCas, utf8File);
								} else {
									CasIOUtils.load(casURL, jCas.getCas());
								}

								DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
								documentMetaData.setDocumentId(cleanDocumentName);
								documentMetaData.setDocumentUri(documentURI);
								documentMetaData.setDocumentTitle(documentName);

								try {
									JCas lCas = JCasFactory.createJCas();
									CasCopier.copyCas(jCas.getCas(), lCas.getCas(), true);

									DocumentMetaData ldocumentMetaData = DocumentMetaData.create(lCas);
									ldocumentMetaData.setDocumentId(cleanDocumentName);
									ldocumentMetaData.setDocumentUri(documentURI);
									ldocumentMetaData.setDocumentTitle(documentName);

									conllEngine.process(lCas);
								} catch (UIMAException e) {
									e.printStackTrace();
								}

								// Reserialize the UTF-8 forced JCas
								if (!forceUTF8 || reserialize) {
									XmiCasSerializer.serialize(jCas.getCas(), new FileOutputStream(utf8File));
								}

								// Write raw text
								if (jCas.getDocumentText() == null || jCas.getDocumentText().isEmpty())
									return;

								String content = jCas.getDocumentText();
								writeToFile(Paths.get(textLocation, cleanDocumentName + ".txt"), content);
							} else {
								throw new HttpResponseException(400, String.format("Request to '%s' failed! Response: %s", documentURI, documentJSON.toString()));
							}
						} catch (HttpResponseException httpE) {
							System.err.println(httpE.getMessage());
						} catch (IOException ioE) {
							System.err.println("Could not write UTF-8 file!");
							ioE.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}
						System.out.printf("\rFile %d/%d (running remote threads: %d, remote pool size: %d)",
								count[0]++, rArray.length(), remotePool.getRunningThreadCount(), remotePool.getPoolSize());
					})).get();

					remotePool.shutdown();
				} catch (UIMAException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			} else {
				System.err.println(remoteFiles);
			}
		} catch (MissingArgumentException e) {
			System.err.println(e.getMessage());
			printUsage();
		}
	}

	private static void printUsage() {
		System.out.println("Downloads and processes the current annotations from a Textannotator repository (14393 by default).\n" +
				"Usage: java -cp $classpaths TextAnnotatorFetch [args]\n" +
				"Arguments:\n" +
				"\t--session, -s\tValid Textannotator session string.\n" +
				"\t--xmi\t\t\tXMI-files download path.\n" +
				"\t--conll\t\t\tConll-files output path.\n" +
				"\t--text\t\t\tPlaintext files output path.\n" +
				"\t--threads N\t\tOptional, the number of threads to use.\n" +
				"\t--forceUTF8\t\tOptional, if set the XMIs will be dowloaded as a string and saved in UTF-8 format.\n" +
				"\t--reserialize\tOptional, use with --forceUTF8. If set the XMIs will be reserialized after download, adding additional metadata.\n" +
				"\t--repository\tOptional, the target repository. Default: 14393.\n" +
				"\t--strategyIndex\tThe conll stacking strategy index. 0=top-down-bottom-up, 1=top-down, 2=bottom-up. Default: 0.");
	}
}
