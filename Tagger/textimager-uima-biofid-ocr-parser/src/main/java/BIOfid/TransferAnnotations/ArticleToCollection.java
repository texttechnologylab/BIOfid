package BIOfid.TransferAnnotations;

import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRDocument;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;


/**
 * Created on 21.03.19
 */
public class ArticleToCollection extends AnnotationTransferHelper {
    static String sFromXMI;
    static String sToXMI;
    static String sOutputPath;
    static String sFileAtlas;
    private static int pThreads = 8;

    public static void main(String[] args) {
        try {
            getParams(args);
            int index;

            index = params.indexOf("-f");
            if (index > -1) {
                sFromXMI = params.get(index + 1);
            } else {
                throw new MissingArgumentException("Missing required argument -f!");
            }

            index = params.indexOf("-t");
            if (index > -1) {
                sToXMI = params.get(index + 1);
            } else {
                throw new MissingArgumentException("Missing required argument -t!");
            }

            index = params.indexOf("-a");
            if (index > -1) {
                sFileAtlas = params.get(index + 1);
            } else {
                throw new MissingArgumentException("Missing required argument -a!");
            }

            index = params.indexOf("-o");
            if (index > -1) {
                sOutputPath = params.get(index + 1);
            } else {
                throw new MissingArgumentException("Missing required argument -o!");
            }

            index = params.indexOf("-p");
            if (index > -1)
                pThreads = Integer.parseInt(params.get(index + 1));

            // Assert Inputs
            File fromXmiDir = new File(sFromXMI);
            assert fromXmiDir.isDirectory();

            File toXmiDir = new File(sToXMI);
            assert toXmiDir.isDirectory();

            File fileAtlasFile = new File(sFileAtlas);
            assert fileAtlasFile.isFile();

//             Create Output Dir
            File outputDir = new File(sOutputPath);
            if (!outputDir.exists())
                outputDir.mkdirs();

            try {
                ArrayList<File> fromList = new ArrayList<>();
                ArrayList<File> toList = new ArrayList<>();
                getFileLists(InputType.PATH, sFromXMI, fromList, sToXMI, toList);

                Map<String, File> fromFileNameMap = fromList.stream()
                        .collect(Collectors.toMap(f -> Files.getNameWithoutExtension(f.getName()), f -> f));
                Map<String, File> toFileNameMap = toList.stream()
                        .collect(Collectors.toMap(f -> Files.getNameWithoutExtension(f.getName()), f -> f));

                // Create collection -> {document} map
                HashMap<String, TreeSet<String>> collectionDocumentMap = new HashMap<>();
                getCollectionDocumentMapFromFileAtlas(fileAtlasFile, collectionDocumentMap);

                final ForkJoinPool forkJoinPool = new ForkJoinPool(pThreads);
                forkJoinPool.submit(() ->
                        collectionDocumentMap.entrySet().parallelStream().forEach(entry -> {
                            final int[] counts = {0, 0};
                            String collectionId = entry.getKey();
                            TreeSet<String> documentSet = entry.getValue();

                            if (toFileNameMap.containsKey(collectionId) && !Sets.intersection(documentSet, fromFileNameMap.keySet()).isEmpty()) {
                                try {
                                    JCas toCas = JCasFactory.createJCas();
                                    File collectionFile = toFileNameMap.get(collectionId);

                                    System.out.println(String.format("[%s (%d/%d)] Loading %s..", Thread.currentThread().getName(), forkJoinPool.getActiveThreadCount(), forkJoinPool.getParallelism(), collectionFile.getName()));
                                    CasIOUtil.readXmi(toCas, collectionFile);
                                    System.out.println(String.format("[%s (%d/%d)] Processing %s\t", Thread.currentThread().getName(), forkJoinPool.getActiveThreadCount(), forkJoinPool.getParallelism(), collectionFile.getName()));

                                    Map<String, OCRDocument> ocrDocumentLookup = JCasUtil.select(toCas, OCRDocument.class)
                                            .stream()
                                            .collect(Collectors.toMap(OCRDocument::getDocumentname, d -> d));

                                    for (String documentId : documentSet) {
                                        if (ocrDocumentLookup.containsKey(documentId) && fromFileNameMap.containsKey(documentId)) {
                                            try {
                                                JCas fromCas = JCasFactory.createJCas();

                                                File documentFile = fromFileNameMap.get(documentId);
                                                CasIOUtil.readXmi(fromCas, documentFile);

                                                OCRDocument ocrDocument = ocrDocumentLookup.get(documentId);
                                                JCas view = ocrDocument.getView().getJCas();
                                                int inititalOffset = ocrDocument.getBegin();
                                                int maximumOffset = ocrDocument.getEnd();

                                                transferAnnotations(fromCas, view, counts, inititalOffset, maximumOffset);
                                            } catch (UIMAException | IOException | NullPointerException e) {
                                                System.err.printf("\n\r[ERROR]\tWhile processing document %s: ", documentId);
                                                e.printStackTrace();
                                                System.err.flush();
                                            }
                                        }
                                    }

                                    System.out.println(String.format("[%s (%d/%d)] Processed %s, transferred %d/%d.", Thread.currentThread().getName(), forkJoinPool.getActiveThreadCount(), forkJoinPool.getParallelism(), collectionFile.getName(), counts[0], counts[1]));
                                    System.out.println(String.format("[%s (%d/%d)] Writing %s..", Thread.currentThread().getName(), forkJoinPool.getActiveThreadCount(), forkJoinPool.getParallelism(), collectionFile.getName()));

                                    // FIXME:remove debug output
//                                    Files.write(JCasUtil.select(toCas, NamedEntity.class)
//                                            .stream()
//                                            .map(ne -> String.format("%s_%s@(%d,%d)", ne.getCoveredText(), ne.getValue(), ne.getBegin(), ne.getEnd()))
//                                            .collect(Collectors.joining(System.lineSeparator())).getBytes(), Paths.get(outputDir.toString(), "temp." + collectionId + ".txt").toFile());

                                    File outFile = Paths.get(outputDir.toString(), collectionId + ".xmi").toFile();
                                    XmiCasSerializer.serialize(toCas.getCas(), new FileOutputStream(outFile));
                                } catch (UIMAException | IOException e) {
                                    System.err.printf("\n\r[ERROR]\tWhile processing collection %s: ", collectionId);
                                    e.printStackTrace();
                                    System.err.flush();
                                } catch (SAXException e) {
                                    System.err.printf("\n\r[ERROR]\tWhile writing processed collection %s: ", collectionId);
                                    e.printStackTrace();
                                    System.err.flush();
                                }
                            }
//                });
                        })).get();
                forkJoinPool.shutdown();

            } catch (IOException | InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

        } catch (MissingArgumentException e) {
            System.err.println(e.getMessage());
        }
    }

    public static void getCollectionDocumentMapFromFileAtlas(File fileAtlasFile, HashMap<String, TreeSet<String>> collectionDocumentMap) throws IOException {
//        TreeMap<String, String> fileAtlas = new TreeMap<>();
//        TreeMap<String, String> documentAtlas = new TreeMap<>();

        String currentPrefix = "";
        for (String line : Files.readLines(fileAtlasFile, StandardCharsets.UTF_8)) {
            String[] split = line.split("\t");

            if (split.length < 2)
                continue;

            if (!StringUtils.isNumeric(split[0])) {
                // Level 0 Case
                currentPrefix = split[1];
            } else {
                String documentId = split[0];
                String documentPath = split[1];
                String collectionId = documentPath.replace(currentPrefix, "")
                        .replaceFirst("^/", "")
                        .split("/")[0];

//                        // fileAtlas update
//                        fileAtlas.put(documentId, documentPath);
//
//                        // documentAtlas update
//                        documentAtlas.put(documentId, collectionId);

                // collectionDocumentMap update
                TreeSet<String> collectionSet = collectionDocumentMap.getOrDefault(collectionId, new TreeSet<>());
                collectionSet.add(documentId);
                collectionDocumentMap.put(collectionId, collectionSet);
            }
        }
    }
}
