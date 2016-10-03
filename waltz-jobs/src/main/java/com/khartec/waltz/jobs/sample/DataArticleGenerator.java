package com.khartec.waltz.jobs.sample;

import com.khartec.waltz.model.data_article.DataFormat;
import com.khartec.waltz.schema.tables.records.DataArticleRecord;
import com.khartec.waltz.service.DIConfiguration;
import org.jooq.DSLContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.khartec.waltz.common.ArrayUtilities.randomPick;
import static com.khartec.waltz.schema.tables.Application.APPLICATION;
import static com.khartec.waltz.schema.tables.DataArticle.DATA_ARTICLE;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Created by dwatkins on 03/10/2016.
 */
public class DataArticleGenerator {

    private static final Random rnd = new Random();



    private static String[] names = {
            "trade",
            "report",
            "risk",
            "ratings",
            "eod",
            "intra-day",
            "yyymmdd",
            "finance",
            "accounting",
            "balance",
            "agg",
            "holdings",
            "accruals",
            "debit",
            "credit",
            "currency",
            "regulatory",
            "transactions",
            "transfers",
            "exchange",
            "summary",
            "daily",
            "position",
            "settlement",
            "confirms",
            "confirmation"
    };


    private static String[] extensions = {
            "xls",
            "txt",
            "csv",
            "tsv",
            "psv",
            "md",
            "bin",
            "xml",
            "json",
            "yaml",
            "yml",
            "pdf",
            "rtf",
            "doc"
    };


    public static void main(String[] args) {
        ApplicationContext ctx = new AnnotationConfigApplicationContext(DIConfiguration.class);

        DSLContext dsl = ctx.getBean(DSLContext.class);

        List<Long> appIds = dsl.select(APPLICATION.ID)
                .from(APPLICATION)
                .fetch(APPLICATION.ID);

        List<DataArticleRecord> records = appIds
                .stream()
                .flatMap(appId -> IntStream
                        .range(0, rnd.nextInt(4))
                        .mapToObj(i -> tuple(appId, i)))
                .map(t -> {
                    String name = mkName();
                    DataArticleRecord record = dsl.newRecord(DATA_ARTICLE);
                    record.setOwningApplicationId(t.v1);
                    record.setFormat(randomPick(DataFormat.values()).name());
                    record.setProvenance("DEMO");
                    record.setDescription("Desc "+ name + " " + t.v2);
                    record.setName(name);
                    record.setExternalId("ext-" + t.v1 + "." + t.v2);
                    return record;
                })
                .collect(Collectors.toList());


        System.out.println("---deleting old demo records");
        dsl.deleteFrom(DATA_ARTICLE)
                .where(DATA_ARTICLE.PROVENANCE.eq("DEMO"))
                .execute();
        System.out.println("---saving: "+records.size());
        dsl.batchInsert(records).execute();
        System.out.println("---done");

    }

    private static String mkName() {

        return new StringBuilder()
                .append(randomPick(names))
                .append("-")
                .append(randomPick(names))
                .append(".")
                .append(randomPick(extensions))
                .toString();
    }
}
