package dev.denisarruda.rinha;

import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.ImmutableGraphIndex;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.disk.GraphIndexWriter;
import io.github.jbellis.jvector.graph.disk.GraphIndexWriterTypes;
import io.github.jbellis.jvector.graph.disk.feature.FeatureId;
import io.github.jbellis.jvector.graph.disk.feature.InlineVectors;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class IndexBuilder {

    private static final VectorTypeSupport vts = VectorizationProvider.getInstance().getVectorTypeSupport();
    static final int DIMENSION = 14;
    static final VectorSimilarityFunction SIMILARITY = VectorSimilarityFunction.EUCLIDEAN;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: IndexBuilder <references.json[.gz]> <index.bin> <labels.bin>");
            System.exit(1);
        }
        Path jsonPath = Path.of(args[0]);
        Path indexPath = Path.of(args[1]);
        Path labelsPath = Path.of(args[2]);

        System.out.printf("Loading %s...%n", jsonPath);
        var vectors = new ArrayList<VectorFloat<?>>();
        var labelList = new ArrayList<Float>();
        try (var is = openStream(jsonPath)) {
            parse(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 1 << 16), vectors, labelList);
        }
        System.out.printf("Loaded %,d vectors%n", vectors.size());

        var ravv = new ListRandomAccessVectorValues(vectors, DIMENSION);

        System.out.println("Building HNSW index...");
        ImmutableGraphIndex index;
        var bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, SIMILARITY);
        try (var builder = new GraphIndexBuilder(bsp, DIMENSION, 16, 100, 1.2f, 1.2f, true, true)) {
            index = builder.build(ravv);
        }
        System.out.println("Index built. Writing to disk...");

        try (var writer = GraphIndexWriter
                .getBuilderFor(GraphIndexWriterTypes.RANDOM_ACCESS_PARALLEL, index, indexPath)
                .with(new InlineVectors(DIMENSION))
                .build()) {
            writer.write(Map.of(
                    FeatureId.INLINE_VECTORS,
                    nodeId -> new InlineVectors.State(ravv.getVector(nodeId))));
        }

        var bb = ByteBuffer.allocate(4 + labelList.size() * 4).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(labelList.size());
        for (float l : labelList) bb.putFloat(l);
        Files.write(labelsPath, bb.array());

        System.out.printf("Done. index.bin=%,d bytes  labels.bin=%,d bytes%n",
                Files.size(indexPath), Files.size(labelsPath));
    }

    private static InputStream openStream(Path path) throws IOException {
        var raw = new FileInputStream(path.toFile());
        return path.toString().endsWith(".gz") ? new GZIPInputStream(raw, 1 << 16) : raw;
    }

    static void parse(BufferedReader r, List<VectorFloat<?>> vecs, List<Float> lbls) throws IOException {
        var st = new StreamTokenizer(r);
        st.resetSyntax();
        st.wordChars('a', 'z');
        st.whitespaceChars(0, ' ');
        st.quoteChar('"');
        st.parseNumbers();

        float[] vec = null;
        int vecIdx = 0;
        boolean readingVec = false;
        boolean readingLabel = false;

        while (st.nextToken() != StreamTokenizer.TT_EOF) {
            if (st.ttype == '"') {
                switch (st.sval) {
                    case "vector" -> { vec = new float[DIMENSION]; vecIdx = 0; readingVec = true; readingLabel = false; }
                    case "label"  -> { readingLabel = true; readingVec = false; }
                    default -> {
                        if (readingLabel && vec != null) {
                            lbls.add("fraud".equals(st.sval) ? 1.0f : 0.0f);
                            vecs.add(vts.createFloatVector(vec));
                            vec = null;
                            readingLabel = false;
                        }
                    }
                }
            } else if (st.ttype == StreamTokenizer.TT_NUMBER && readingVec && vecIdx < DIMENSION) {
                vec[vecIdx++] = (float) st.nval;
            }
        }
    }
}
