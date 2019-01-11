package site.francesco.export.nodes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import com.google.common.escape.CharEscaperBuilder;
import com.google.common.escape.Escaper;
import org.apache.jackrabbit.oak.api.Blob;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.blob.datastore.DataStoreBlobStore;
import org.apache.jackrabbit.oak.plugins.blob.datastore.OakFileDataStore;
import org.apache.jackrabbit.oak.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.segment.SegmentNodeStoreBuilders;
import org.apache.jackrabbit.oak.segment.SegmentNotFoundException;
import org.apache.jackrabbit.oak.segment.file.FileStoreBuilder;
import org.apache.jackrabbit.oak.segment.file.ReadOnlyFileStore;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.util.Base64;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "export-nodes",
    description = "Export nodes to from an Segment Store to a text based intermediate format."
)
public class ExportNodes implements Runnable {

    private final Set<String> notFoundSegments = new HashSet<>();

    @Option(
        names = "--segment-store",
        paramLabel = "PATH",
        required = true,
        description = "Path to the Segment Store"
    )
    private File segmentStorePath;

    @Option(
        names = "--file-data-store",
        paramLabel = "PATH",
        description = "Path to the File Data Store"
    )
    private File fileDataStorePath;

    public void run() {
        FileStoreBuilder builder = FileStoreBuilder.fileStoreBuilder(segmentStorePath);

        if (fileDataStorePath != null) {
            OakFileDataStore fileDataStore = new OakFileDataStore();
            fileDataStore.init(fileDataStorePath.getAbsolutePath());
            builder.withBlobStore(new DataStoreBlobStore(fileDataStore, true));
        }

        try (ReadOnlyFileStore fileStore = builder.buildReadOnly()) {
            SegmentNodeStore nodeStore = SegmentNodeStoreBuilders.builder(fileStore).build();
            exportRoot(nodeStore.getRoot());

        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void exportRoot(NodeState node) {
        System.out.printf("r\n");
        exportNode(node);
        System.out.printf("^\n");
    }

    private void exportNode(NodeState node) {
        exportProperties(node);
        exportChildren(node);
    }

    private void exportChildren(NodeState node) {
        try {
            for (String name : node.getChildNodeNames()) {
                exportChild(node, name);
            }
        } catch (SegmentNotFoundException e) {
            handle(e);
        }
    }

    private void exportChild(NodeState parent, String name) {
        try {
            NodeState child = parent.getChildNode(name);
            System.out.printf("c %s\n", name);
            exportNode(child);
            System.out.printf("^\n");
        } catch (SegmentNotFoundException e) {
            handle(e);
        }
    }

    private void exportProperties(NodeState node) {
        try {
            for (PropertyState property : node.getProperties()) {
                exportProperty(property);
            }
        } catch (SegmentNotFoundException e) {
            handle(e);
        }
    }

    private void exportProperty(PropertyState property) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bos);

        try {
            out.printf("p %s %s\n", property.getType(), property.getName());

            if (property.isArray()) {
                exportArrayProperty(out, property);
            } else {
                exportScalarProperty(out, property);
            }

            out.printf("^\n");

            System.out.write(bos.toByteArray());
        } catch (SegmentNotFoundException e) {
            handle(e);
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private void exportScalarProperty(PrintStream out, PropertyState property) throws IOException {
        if (property.getType().equals(Type.BINARY)) {
            exportBinaryValue(out, property.getValue(Type.BINARY));
        } else {
            exportStringValue(out, property.getValue(Type.STRING));
        }
    }

    private void exportArrayProperty(PrintStream out, PropertyState property) throws IOException {
        if (property.getType().equals(Type.BINARIES)) {
            for (int i = 0; i < property.count(); i++) {
                exportBinaryValue(out, property.getValue(Type.BINARY, i));
            }
        } else {
            for (int i = 0; i < property.count(); i++) {
                exportStringValue(out, property.getValue(Type.STRING, i));
            }
        }
    }

    private void exportBinaryValue(PrintStream out, Blob blob) throws IOException {
        out.printf("x ");
        Base64.encode(blob.getNewStream(), out);
        out.printf("\n");
    }

    private static final Escaper ESCAPER = new CharEscaperBuilder()
        .addEscape('\n', "\\n")
        .addEscape('\\', "\\\\")
        .toEscaper();

    private void exportStringValue(PrintStream out, String value) {
        out.printf("v %s\n", ESCAPER.escape(value));
    }

    private void handle(SegmentNotFoundException e) {
        if (notFoundSegments.add(e.getSegmentId())) {
            e.printStackTrace(System.err);
        }
    }

    public static void main(String... args) {
        CommandLine.run(new ExportNodes(), args);
    }

}
