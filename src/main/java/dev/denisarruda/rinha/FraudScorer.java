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
import java.util.Random;

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
        } catch (IOException ignored) {
        }

        if (vectors.isEmpty()) {
            loadSynthetic(vectors, labelList);
        }

        LABELS = new float[labelList.size()];
        for (int i = 0; i < labelList.size(); i++) LABELS[i] = labelList.get(i);

        RAVV = new ListRandomAccessVectorValues(vectors, DIMENSION);
        INDEX = buildIndex(RAVV);
        SEARCHER = ThreadLocal.withInitial(() -> new GraphSearcher(INDEX));
    }

    private static void loadFromResource(List<VectorFloat<?>> vecs, List<Float> lbls) throws IOException {
        var is = FraudScorer.class.getResourceAsStream("/references.json");
        if (is == null) return;
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

    private static void loadSynthetic(List<VectorFloat<?>> vectors, List<Float> labelList) {
        float[][] legitProtos = {
            {0.05f, 0.08f, 0.10f, 0.48f, 0.33f,  0.28f,  0.02f, 0.02f, 0.05f, 0f, 1f, 0f, 0.15f, 0.03f},
            {0.03f, 0.08f, 0.08f, 0.39f, 0.14f,  0.35f,  0.01f, 0.03f, 0.05f, 0f, 1f, 0f, 0.20f, 0.02f},
            {0.04f, 0.08f, 0.12f, 0.87f, 0.28f,  0.22f,  0.02f, 0.05f, 0.10f, 0f, 1f, 0f, 0.30f, 0.04f},
            {0.08f, 0.25f, 0.15f, 0.48f, 0.57f,  0.42f,  0.00f, 0.00f, 0.15f, 1f, 0f, 0f, 0.25f, 0.08f},
            {0.15f, 0.33f, 0.18f, 0.61f, 0.42f, -1.00f, -1.00f, 0.05f, 0.05f, 0f, 1f, 0f, 0.25f, 0.12f},
            {0.03f, 0.08f, 0.07f, 0.74f, 0.14f,  0.18f,  0.04f, 0.08f, 0.10f, 0f, 1f, 0f, 0.30f, 0.03f},
            {0.20f, 0.17f, 0.25f, 0.39f, 0.00f,  0.52f,  0.00f, 0.00f, 0.10f, 1f, 0f, 0f, 0.35f, 0.18f},
            {0.02f, 0.08f, 0.05f, 0.57f, 0.71f,  0.38f,  0.00f, 0.00f, 0.20f, 1f, 0f, 0f, 0.25f, 0.02f},
        };
        float[][] fraudProtos = {
            {0.55f, 0.08f, 0.85f, 0.13f, 0.42f, 0.05f, 0.00f, 0.00f, 0.55f, 1f, 0f, 1f, 0.85f, 0.45f},
            {0.35f, 0.17f, 0.60f, 0.30f, 0.28f, 0.10f, 0.70f, 0.85f, 0.40f, 0f, 1f, 1f, 0.50f, 0.30f},
            {0.40f, 0.08f, 0.55f, 0.04f, 0.14f, 0.02f, 0.95f, 0.60f, 0.30f, 0f, 1f, 1f, 0.45f, 0.35f},
            {0.30f, 0.08f, 0.50f, 0.04f, 0.57f, 0.03f, 0.40f, 0.40f, 0.90f, 1f, 0f, 1f, 0.80f, 0.25f},
            {0.70f, 0.08f, 0.95f, 0.22f, 0.42f, 0.15f, 0.30f, 0.50f, 0.20f, 1f, 0f, 1f, 0.60f, 0.50f},
            {0.25f, 0.08f, 0.40f, 0.04f, 0.28f, 0.08f, 0.00f, 0.00f, 0.45f, 1f, 0f, 1f, 0.75f, 0.20f},
            {0.03f, 0.08f, 0.06f, 0.17f, 0.28f, 0.03f, 0.10f, 0.25f, 0.95f, 0f, 1f, 1f, 0.50f, 0.03f},
            {0.45f, 0.08f, 0.70f, 0.09f, 0.14f, 0.06f, 0.80f, 0.75f, 0.35f, 1f, 0f, 1f, 0.85f, 0.40f},
        };

        var rng = new Random(42L);
        for (float[] proto : legitProtos) {
            for (int i = 0; i < 5; i++) {
                vectors.add(vts.createFloatVector(vary(proto, rng, 0.04f)));
                labelList.add(0.0f);
            }
        }
        for (float[] proto : fraudProtos) {
            for (int i = 0; i < 5; i++) {
                vectors.add(vts.createFloatVector(vary(proto, rng, 0.04f)));
                labelList.add(1.0f);
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

    private static float[] vary(float[] proto, Random rng, float noise) {
        float[] v = proto.clone();
        for (int i = 0; i < v.length; i++) {
            if (v[i] < 0f || i == 9 || i == 10 || i == 11) continue;
            v[i] = Math.max(0f, Math.min(1f, v[i] + (float) (rng.nextGaussian() * noise)));
        }
        return v;
    }
}
