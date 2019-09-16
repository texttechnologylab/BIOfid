package BIOfid.TransferAnnotations;

import BIOfid.AbstractRunner;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.jetbrains.annotations.NotNull;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.select;

abstract class AnnotationTransferHelper extends AbstractRunner {

    static boolean transferAnnotations(JCas fromCas, JCas toCas, final int[] counts, final int initialOffset, final int maximumOffset) {
        if (Objects.isNull(fromCas.getDocumentText()) || Objects.isNull(toCas.getDocumentText()))
            return true;

        HashSet<TOP> fingerprinted = select(fromCas, Fingerprint.class).stream().map(Fingerprint::getReference).collect(Collectors.toCollection(HashSet::new));
        List<NamedEntity> namedEntities = select(fromCas, NamedEntity.class).stream().filter(fingerprinted::contains).sorted(Comparator.comparingInt(NamedEntity::getBegin)).collect(Collectors.toList());

        if (namedEntities.isEmpty())
            return true;

        if (fromCas.getDocumentText().equals(toCas.getDocumentText().substring(initialOffset, maximumOffset))) {
            for (NamedEntity neSource : namedEntities) {
                NamedEntity neTarget = getNamedEntity(toCas, neSource, neSource.getBegin() + initialOffset, neSource.getEnd() + initialOffset);
                toCas.addFsToIndexes(neTarget);
                counts[0]++;
                counts[1]++;
            }
        } else if (fromCas.getDocumentText().contains(toCas.getDocumentText().substring(initialOffset, maximumOffset))) {
            int offset = fromCas.getDocumentText().indexOf(toCas.getDocumentText().substring(initialOffset, maximumOffset));
            for (NamedEntity neSource : namedEntities) {
                NamedEntity neTarget = getNamedEntity(toCas, neSource, neSource.getBegin() + offset + initialOffset, neSource.getEnd() + offset + initialOffset);
                toCas.addFsToIndexes(neTarget);
                counts[0]++;
                counts[1]++;
            }
        } else {
            HashMap<String, Integer> lastTargetOffsetMap = new HashMap<>();
            for (NamedEntity neSource : namedEntities) {
                String coveredText = neSource.getCoveredText();
                int lastTargetOffset = lastTargetOffsetMap.getOrDefault(coveredText, initialOffset);
                int index = toCas.getDocumentText().indexOf(coveredText, lastTargetOffset);
                if (index > -1 && index < maximumOffset) {
                    counts[0]++;
                    NamedEntity neTarget = getNamedEntity(toCas, neSource, index, index + coveredText.length());
                    toCas.addFsToIndexes(neTarget);
                    lastTargetOffsetMap.put(coveredText, neTarget.getEnd());
                }
                counts[1]++;
            }
        }
        return false;
    }

    @NotNull
    private static NamedEntity getNamedEntity(JCas toCas, NamedEntity neSource, int begin, int end) {
        NamedEntity neTarget = new NamedEntity(toCas, begin, end);
        neTarget.setValue(neSource.getValue());
        neTarget.setIdentifier(neSource.getIdentifier());
        return neTarget;
    }

    enum InputType {
        REGEX, LIST, PATH
    }

    static void getFileLists(InputType inputType, String sFrom, ArrayList<File> fromList, String sTo, ArrayList<File> toList) throws IOException {
        if (inputType == InputType.REGEX) {
            throw new NotImplementedException("InputType.REGEX is not implemented yet.");
        } else if (inputType == InputType.PATH) {
            getFileListsFromPath(sFrom, fromList, sTo, toList);
        } else { // InputType.LIST
            getFileListsFromList(sFrom, fromList, sTo, toList);
        }
    }

    private static void getFileListsFromList(String sFrom, ArrayList<File> fromList, String sTo, ArrayList<File> toList) throws IOException {
        File fromNode = new File(sFrom);
        File toNode = new File(sTo);

        if (!fromNode.exists() || !fromNode.isFile())
            throw new IllegalArgumentException(String.format("'%s' is not a valid file path!", sFrom));
        if (!toNode.exists() || !toNode.isFile())
            throw new IllegalArgumentException(String.format("'%s' is not a valid file path!", sTo));

        fromList.addAll(Files.readLines(fromNode, StandardCharsets.UTF_8).stream().map(File::new).collect(Collectors.toCollection(ArrayList::new)));
        toList.addAll(Files.readLines(toNode, StandardCharsets.UTF_8).stream().map(File::new).collect(Collectors.toCollection(ArrayList::new)));
    }

    private static void getFileListsFromPath(String sFrom, ArrayList<File> fromList, String sTo, ArrayList<File> toList) {
        File fromNode = Paths.get(sFrom).toAbsolutePath().toFile();
        File toNode = Paths.get(sTo).toAbsolutePath().toFile();

        if (fromNode.isFile())
            throw new IllegalArgumentException(String.format("'%s' is not a valid directory path!", sFrom));
        if (toNode.isFile())
            throw new IllegalArgumentException(String.format("'%s' is not a valid directory path!", sTo));

        fromList.addAll(Streams.stream(Files.fileTraverser().breadthFirst(fromNode)).filter(File::isFile).collect(Collectors.toList()));
        toList.addAll(Streams.stream(Files.fileTraverser().breadthFirst(toNode)).filter(File::isFile).collect(Collectors.toList()));
    }
}
