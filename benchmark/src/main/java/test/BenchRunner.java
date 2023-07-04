package test;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

public class BenchRunner {

    public static void main(String[] args) throws RunnerException, IOException {

        Options baseOpts = new OptionsBuilder()
                .include(Benchie.class.getName())
                .warmupTime(TimeValue.milliseconds(1000))
                .measurementTime(TimeValue.milliseconds(1000))
                .warmupIterations(5)
                .measurementIterations(5)
                .forks(5)
                .verbosity(VerboseMode.EXTRA)
                .build();

        Collection<RunResult> results = new Runner(baseOpts).run();
        results.forEach( res -> {
            System.out.println(res.getPrimaryResult().getLabel());
        });

        RunResult base = results.stream().filter(res -> res.getPrimaryResult().getLabel().equals("baseline")).findFirst().get();
        RunResult sdx = results.stream().filter(res -> res.getPrimaryResult().getLabel().equals("sdxTest")).findFirst().get();

        FileWriter writer = new FileWriter("./result.txt");

        results.forEach(res -> {
            try {
                writer.append(res.getPrimaryResult().getLabel() + ":\n");
                writer.append("\tmean:\t" +
                        res.getPrimaryResult().getStatistics().getMean() + "\n");
                writer.append("\tstdev:\t" +
                        res.getPrimaryResult().getStatistics().getStandardDeviation() + "\n");
                writer.append("\t#:\t" +
                        res.getPrimaryResult().getStatistics().getN() + "\n");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        boolean isDifferent999 = base.getPrimaryResult().getStatistics().isDifferent(sdx.getPrimaryResult().getStatistics(), 0.999);
        boolean isDifferent99 = base.getPrimaryResult().getStatistics().isDifferent(sdx.getPrimaryResult().getStatistics(), 0.99);
        boolean isDifferent95 = base.getPrimaryResult().getStatistics().isDifferent(sdx.getPrimaryResult().getStatistics(), 0.95);

        if(isDifferent999){
            writer.append("\n\n" +
                    "is different with 99.9% confidence");
        } else if (isDifferent99){
            writer.append("\n\n" +
                    "is different with 99% confidence");
        } else if (isDifferent95){
            writer.append("\n\n" +
                    "is different with 95% confidence");
        } else {
            writer.append("\n\nis not statistically different");
        }

        writer.close();




    }
}
