package BIOfid.TransferAnnotations;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static BIOfid.TransferAnnotations.AnnotationTransferHelper.InputType.*;
import static org.apache.uima.fit.util.JCasUtil.select;

/**
 * Created on 11.03.19
 */
public class NaiveByFilename extends AnnotationTransferHelper {
    private static String sFrom;
    private static String sTo;
    private static String sOut;
    private static AnnotationTransferHelper.InputType inputType;
    private static Path outDir;

    public static void main(String[] args) {
        try {
            getParams(args);

            int index;
            index = Integer.max(params.indexOf("-f"), params.indexOf("--from"));
            if (index > -1) {
                sFrom = params.get(index + 1);
            } else {
                throw new MissingArgumentException("Missing --from!\n");
            }

            index = Integer.max(params.indexOf("-t"), params.indexOf("--to"));
            if (index > -1) {
                sTo = params.get(index + 1);
            } else {
                throw new MissingArgumentException("Missing --to\n");
            }

            index = Integer.max(params.indexOf("-o"), params.indexOf("--out"));
            if (index > -1) {
                outDir = Paths.get(params.get(index + 1));
                if (!outDir.toFile().exists())
                    throw new InvalidPathException(outDir.toString(), "Output directory does not exist!");
                if (outDir.toFile().isFile())
                    throw new InvalidPathException(outDir.toString(), "Output directory is a file!");
            } else {
                throw new MissingArgumentException("Missing --out\n");
            }

            index = Integer.max(params.indexOf("-i"), params.indexOf("--input-type"));
            if (index > -1) {
                switch (params.get(index + 1)) {
                    case "r":
                    case "regex":
                    case "R":
                    case "REGEX":
                        inputType = REGEX;
                        break;
                    case "l":
                    case "list":
                    case "L":
                    case "LIST":
                        inputType = LIST;
                        break;
                    case "p":
                    case "path":
                    case "P":
                    case "PATH":
                        inputType = PATH;
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("'%s' is not a valid InputType! Valid types are: PATH, REGEX, LIST.", params.get(index + 1)));
                }
            } else {
                throw new MissingArgumentException("Missing --input-type\n");
            }

            ArrayList<File> fromList = new ArrayList<>();
            ArrayList<File> toList = new ArrayList<>();

            // Get unordered lists
            AnnotationTransferHelper.getFileLists(inputType, sFrom, fromList, sTo, toList);

            HashSet<ImmutablePair<File, File>> pairs = mapFilesByName(fromList, toList);

            alignAnnotations(pairs);

//			System.out.printf("Aligned %d perfect matches!", count);
        } catch (IllegalArgumentException | MissingArgumentException | IOException e) {
            e.printStackTrace();
        }
    }

    public static void alignAnnotations(HashSet<ImmutablePair<File, File>> pairs) {
        int totalCount = 0;
        int totalAllCount = 0;
        ArrayList<Double> avg = new ArrayList<>();

        for (ImmutablePair<File, File> pair : pairs) {
            File fromFile = pair.getLeft();
            File toFile = pair.getRight();

            try {
                JCas fromCas = JCasFactory.createJCas();
                CasIOUtil.readXmi(fromCas, fromFile);

                JCas toCas = JCasFactory.createJCas();
                CasIOUtil.readXmi(toCas, toFile);

                int counts[] = {0, 0};

                if (transferAnnotations(fromCas, toCas, counts, 0, toCas.getDocumentText().length())) continue;

                totalCount += counts[0];
                totalAllCount += counts[1];
                avg.add(counts[0] * 1.0 / counts[1]);

                XmiCasSerializer.serialize(toCas.getCas(), new FileOutputStream(Paths.get(outDir.toString(), toFile.getName()).toFile()));

                System.out.printf("Found %d/%d NEs in '%s'.\n", counts[0], counts[1], fromFile.getName());
                System.out.flush();
            } catch (UIMAException | IOException | SAXException e) {
                e.printStackTrace();
            }
        }
        System.out.printf("Found total %d/%d NEs, avg. precision %01.3f.\n", totalCount, totalAllCount, avg.stream().reduce(0.0, (a, b) -> a + b) / avg.size());
    }

    private static HashSet<ImmutablePair<File, File>> mapFilesByName(ArrayList<File> fromList, ArrayList<File> toList) {
        Map<String, File> map = toList.stream().collect(Collectors.toMap(File::getName, f -> f));
        ArrayList<String> missed = new ArrayList<>();

        HashSet<ImmutablePair<File, File>> pairs = new HashSet<>();

        for (File from : fromList) {
            if (map.containsKey(from.getName())) {
                pairs.add(ImmutablePair.of(from, map.get(from.getName())));
            } else {
                missed.add(from.getPath());
            }
        }

        System.out.printf("Matched %d pairs.\n", pairs.size());
        pairs.forEach(System.out::println);
        System.out.flush();

        System.err.printf("Missed %d pairs.\n", missed.size());
        System.err.println(missed);
        System.err.flush();

        return pairs;
    }
}
