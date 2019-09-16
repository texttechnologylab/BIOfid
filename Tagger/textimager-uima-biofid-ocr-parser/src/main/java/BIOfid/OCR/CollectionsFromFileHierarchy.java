package BIOfid.OCR;

import BIOfid.Utility.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CollectionsFromFileHierarchy extends AbstractOCRParser {
	
	private static ArrayList<String> sFileRootPaths;
	private static String sOutputPath;
	private static String sArticleOutputPath = null;
	private static String sVocabularyPath;
	private static String sRawOutput;
	private static int depth = 1;
	//	private static int documentDepth = 3;
	private static boolean sortAlNum = false;
	
	private static final Predicate<File> isLeafDir = dir -> Arrays.stream(Objects.requireNonNull(dir.listFiles())).noneMatch(File::isDirectory);
	private static final String spaces = StringUtils.repeat(' ', 20);
	
	public static void main(String[] args) {
		Options options = new Options();
		
		options.addOption("h", "help", false, "Print this message.");
		Option inputOption = new Option("i", "input", true, "Input root paths.");
		inputOption.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(inputOption);
		options.addOption("o", "output", true, "Output path.");
		options.addOption("v", "vocab", true, "Vocabulary path.");
		options.addOption("d", "depth", true, "The target collection root depth.");
		options.addOption("txt", "raw", true, "Optional, raw text output path.");
		
		options.addOption("e", "exportArticles", true, "Optional, path to per article export location. If set all articles contained in the collections will be exported separately to the given path.");
		
		options.addOption("s", "sortAlNum", false, "Optional, if true re-sort document level files alpha-numerically. Otherwise, the files will be in depth-first pre-order sequence.");
		
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(options, args);
			
			if (cmd.hasOption("h")) {
				printUsage(options);
				return;
			}
			
			if (cmd.hasOption("i")) {
				sFileRootPaths = Lists.newArrayList(cmd.getOptionValues("i"));
			} else {
				throw new MissingArgumentException("Missing --input!\n");
			}
			
			if (cmd.hasOption("o")) {
				sOutputPath = cmd.getOptionValue("o");
				new File(sOutputPath).mkdirs();
			} else {
				throw new MissingArgumentException("Missing --output!\n");
			}
			
			if (cmd.hasOption("v")) {
				sVocabularyPath = cmd.getOptionValue("v");
			} else {
				throw new MissingArgumentException("Missing --vocab!\n");
			}
			
			if (cmd.hasOption("d")) {
				depth = Integer.parseInt(cmd.getOptionValue("d"));
			}
			
			if (cmd.hasOption("txt")) {
				sRawOutput = cmd.getOptionValue("txt");
			}
			
			sortAlNum = cmd.hasOption("s");
			
			if (cmd.hasOption("e")) {
				sArticleOutputPath = cmd.getOptionValue("e");
				new File(sArticleOutputPath).mkdirs();
			}
			
			// Maps all subdirectories of the input roots as absolute files to their relative depth
			final Map<File, Integer> dirDepthMap = sFileRootPaths.stream()
					.map(root -> Streams.stream(Files.fileTraverser().depthFirstPreOrder(new File(root)))
							.filter(File::isDirectory)
							.collect(Collectors.toMap(File::getAbsoluteFile,
									file -> Util.getRelativeDepth(root, file))))
					.flatMap(m -> m.entrySet().stream())
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			
			for (String rootPath : sFileRootPaths) {
				dirDepthMap.remove(new File(rootPath).getAbsoluteFile());
			}
			
			// Get all collection root directories, as given their relative depth to the input path
			ImmutableList<File> collectionDirs = ImmutableList.copyOf(dirDepthMap.entrySet().stream()
					.filter(e -> e.getKey().isDirectory())
					.filter(e -> e.getValue() == depth)
					.map(Map.Entry::getKey)
					.collect(Collectors.toList()));
			
			long documentCount = dirDepthMap.entrySet().stream()
					.filter(e -> e.getKey().isDirectory())
					.filter(e -> isLeafDir.test(e.getKey()))
					.count();
			System.out.printf("Starting parsing %d collections with %d documents..\n", collectionDirs.size(), documentCount);
			
			AtomicInteger count = new AtomicInteger(0);
			
			// Parse each collection
			collectionDirs.parallelStream().forEach(documentParentDir -> {
				ArrayList<String> files = getFilePaths(documentParentDir);
				if (files.size() == 0)
					return;
				
				String documentId = documentParentDir.getName();
				
				// Main parser call
				try {
					processDocumentPathList(sOutputPath, sVocabularyPath, sRawOutput, documentId, files, true, documentParentDir, sArticleOutputPath);
				} catch (UIMAException e) {
					System.err.printf(
							"Caught UIMAException while parsing collection %s!\n" + "%s\n" + "\t%s\n" +
									"Caused by: %s\n" + "\t%s\n",
							documentId, e.toString(), e.getStackTrace()[0].toString(),
							e.getCause().toString(), e.getCause().getStackTrace()[0].toString());
				}
				
				System.out.printf("\r%d/%d Parsed collection %s.%s\n",
						count.incrementAndGet(), collectionDirs.size(), documentId, spaces);
				AbstractOCRParser.printProgress(true);
			});
			
			System.out.println("\r\n\nFinished parsing.");
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			System.err.println(Arrays.toString(args));
			System.out.println();
			
			printUsage(options);
		}
	}
	
	@NotNull
	public static ArrayList<String> getFilePaths(File documentParentDir) {
		ArrayList<String> files;
		if (sortAlNum) {
			files = Streams.stream(Files.fileTraverser().depthFirstPreOrder(documentParentDir))
					.sequential()
					.filter(File::isFile)
					.sorted(Comparator.comparing(File::getName))
					.map(File::getAbsolutePath)
					.collect(Collectors.toCollection(ArrayList::new));
		} else {
			files = Streams.stream(Files.fileTraverser().depthFirstPreOrder(documentParentDir))
					.sequential()
					.filter(File::isFile)
					.map(File::getAbsolutePath)
					.collect(Collectors.toCollection(ArrayList::new));
		}
		return files;
	}
	
	private static void printUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("java -cp $CP BIOfid.OCR.CollectionsFromFileHierarchy",
				"Process XML Abby FineReader exports from entire collections by traversing a file hierarchy. The root path should be the top folder of a collection (Band/Zeitschrift).",
				options,
				"",
				true);
	}
}
