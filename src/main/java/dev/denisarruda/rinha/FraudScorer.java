package dev.denisarruda.rinha;

import io.github.jbellis.jvector.disk.ReaderSupplierFactory;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.DefaultSearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

class FraudScorer {

    private static final VectorTypeSupport vts = VectorizationProvider.getInstance().getVectorTypeSupport();
    private static final int TOP_K = 5;
    private static final VectorSimilarityFunction SIMILARITY = VectorSimilarityFunction.EUCLIDEAN;

    private static final OnDiskGraphIndex INDEX;
    private static final float[] LABELS;
    private static final ThreadLocal<GraphSearcher> SEARCHER;

    static {
        try {
            var indexPath = Path.of("/app/index.bin");
            INDEX = OnDiskGraphIndex.load(ReaderSupplierFactory.open(indexPath));
            LABELS = loadLabels(Path.of("/app/labels.bin"));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        SEARCHER = ThreadLocal.withInitial(() -> new GraphSearcher(INDEX));
    }

    private static float[] loadLabels(Path path) throws IOException {
        var bb = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        int count = bb.getInt();
        float[] labels = new float[count];
        for (int i = 0; i < count; i++) labels[i] = bb.getFloat();
        return labels;
    }

    static void init() {}

    static float score(float[] features) {
        var query = vts.createFloatVector(features);
        var searcher = SEARCHER.get();
        var graphRavv = (RandomAccessVectorValues) searcher.getView();
        var ssp = DefaultSearchScoreProvider.exact(query, SIMILARITY, graphRavv);
        var result = searcher.search(ssp, TOP_K, Bits.ALL);

        int fraudCount = 0;
        for (SearchResult.NodeScore ns : result.getNodes()) {
            if (LABELS[ns.node] == 1.0f) fraudCount++;
        }
        return fraudCount / (float) TOP_K;
    }
}
