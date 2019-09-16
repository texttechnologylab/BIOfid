package BIOfid.OCR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.uima.UIMAException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentsFromMetadata extends AbstractOCRParser {
	
	public static void main(String[] args) {
		System.out.printf("Running DocumentsFromMetadata with options: %s\n", Arrays.toString(args));
		String sMetadataPath = args[0];
		String sFileAtlasPath = args[1];
		String sOutputPath = args[2];
		String sVocabularyPath = args[3];
		String sRawPath = args[4];
		
		try {
			ImmutableMap<String, String> fileAtlas = loadFileAtlas(Paths.get(sFileAtlasPath));
			System.out.printf("Loaded atlas with %d entries.\n", fileAtlas.size());
			ArrayList<ImmutableList<String>> metadata = loadMetadata(Paths.get(sMetadataPath));
			System.out.printf("Loaded metadata for %d documents.\n", metadata.size());
			
			System.out.println("Starting document parsing..");
			
			AtomicInteger count = new AtomicInteger(0);
			
			metadata.parallelStream().forEach(documentParts -> {
//			for (ImmutableList<String> documentParts : metadata) {
				String documentId = documentParts.get(0);
				System.out.printf("%d/%d Parsing document with id %s..\n", count.incrementAndGet(), metadata.size(), documentId);
				
				try {
					ArrayList<String> pathList = new ArrayList<>();
					for (String document : documentParts) {
						String path = fileAtlas.getOrDefault(document, null);
						if (path != null && new File(path).isFile()) pathList.add(path);
					}
					
					processDocumentPathList(sOutputPath, sVocabularyPath, sRawPath, documentId, pathList);
				} catch (UIMAException e) {
					System.err.printf(
							"Caught UIMAException while parsing document %s!\n" +
									"%s\n" +
									"\t%s\n" +
									"Caused by: %s\n" +
									"\t%s\n",
							documentId,
							e.toString(),
							e.getStackTrace()[0].toString(),
							e.getCause().toString(),
							e.getCause().getStackTrace()[0].toString()
					);
				}
			});
			System.out.println("\nFinished parsing.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	static private ArrayList<ImmutableList<String>> loadMetadata(Path pMetadataPath) throws IOException {
		ArrayList<ImmutableList<String>> metadata = new ArrayList<>();
		String[] metadataLines = Files.newBufferedReader(pMetadataPath, StandardCharsets.UTF_8).lines().toArray(String[]::new);
		
		ArrayList<String> currentMetaDocument = new ArrayList<>();
//		String currentMetaDocumentName;
		/// Skip first line
		for (int i = 1; i < metadataLines.length; i++) {
			String[] split = metadataLines[i].split("\t");
			if (split.length > 2 && !split[2].isEmpty()) {
//				currentMetaDocumentName = split[0];
				if (!currentMetaDocument.isEmpty())
					metadata.add(ImmutableList.copyOf(currentMetaDocument));
				currentMetaDocument = new ArrayList<>();
			}
			currentMetaDocument.add(split[0]);
		}
		
		return metadata;
	}
	
	static private ImmutableMap<String, String> loadFileAtlas(Path pFileAtlasPath) throws IOException, NullPointerException {
		HashMap<String, String> fileAtlas = new HashMap<>();
		try (BufferedReader bufferedReader = com.google.common.io.Files.newReader(pFileAtlasPath.toFile(), StandardCharsets.UTF_8)) {
			bufferedReader.lines().map(l -> l.split("\t")).forEach(arr -> fileAtlas.put(arr[0], arr[1]));
		}
		if (fileAtlas.isEmpty()) {
			throw new NullPointerException("Files could not be found!");
		}
		return ImmutableMap.copyOf(fileAtlas);
	}
}
