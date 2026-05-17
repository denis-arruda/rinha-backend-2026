package dev.denisarruda.rinha;

import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ImmutableGraphIndex;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

class FraudScorer {

    private static final VectorTypeSupport vts = VectorizationProvider.getInstance().getVectorTypeSupport();
    private static final int DIMENSION = 14;
    private static final int TOP_K = 5;
    private static final VectorSimilarityFunction SIMILARITY = VectorSimilarityFunction.EUCLIDEAN;

    private static final ImmutableGraphIndex INDEX;
    private static final float[] LABELS;
    private static final RandomAccessVectorValues RAVV;
    private static final ThreadLocal<GraphSearcher> SEARCHER;

    static {
        List<VectorFloat<?>> vectors = new ArrayList<>();
        List<Float> labelList = new ArrayList<>();

        try {
            loadFromResource(vectors, labelList);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }

        if (vectors.isEmpty()) {
            throw new ExceptionInInitializerError("references.json not found or is empty");
        }

        LABELS = new float[labelList.size()];
        for (int i = 0; i < labelList.size(); i++) LABELS[i] = labelList.get(i);

        RAVV = new ListRandomAccessVectorValues(vectors, DIMENSION);
        INDEX = buildIndex(RAVV);
        SEARCHER = ThreadLocal.withInitial(() -> new GraphSearcher(INDEX));
    }

    private static void loadFromResource(List<VectorFloat<?>> vecs, List<Float> lbls) throws IOException {
        var is = FraudScorer.class.getResourceAsStream("/references.json");
        if (is == null) throw new IOException("references.json not found in classpath");
        try (var r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 1 << 16)) {
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

    private static ImmutableGraphIndex buildIndex(RandomAccessVectorValues ravv) {
        var bsp = BuildScoreProvider.randomAccessScoreProvider(ravv, SIMILARITY);
        try (var builder = new GraphIndexBuilder(bsp, DIMENSION, 16, 100, 1.2f, 1.2f, true, true)) {
            return builder.build(ravv);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static void init() {}

    static float score(float[] features) {
        var query = vts.createFloatVector(features);
        var ssp = DefaultSearchScoreProvider.exact(query, SIMILARITY, RAVV);
        var result = SEARCHER.get().search(ssp, TOP_K, Bits.ALL);

        int fraudCount = 0;
        for (SearchResult.NodeScore ns : result.getNodes()) {
            if (LABELS[ns.node] == 1.0f) fraudCount++;
        }
        return fraudCount / (float) TOP_K;
    }

}
