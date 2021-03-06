/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016  Khartec Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.khartec.waltz.jobs.generators;

import com.khartec.waltz.common.ArrayUtilities;
import com.khartec.waltz.common.ListUtilities;
import com.khartec.waltz.common.RandomUtilities;
import com.khartec.waltz.data.physical_specification.PhysicalSpecificationDao;
import com.khartec.waltz.model.Criticality;
import com.khartec.waltz.model.enum_value.EnumValueKind;
import com.khartec.waltz.model.physical_flow.FrequencyKind;
import com.khartec.waltz.model.physical_specification.PhysicalSpecification;
import com.khartec.waltz.schema.tables.records.PhysicalFlowRecord;
import org.jooq.DSLContext;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple3;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.khartec.waltz.common.CollectionUtilities.isEmpty;
import static com.khartec.waltz.common.RandomUtilities.randomPick;
import static com.khartec.waltz.common.ListUtilities.newArrayList;
import static com.khartec.waltz.common.MapUtilities.groupBy;
import static com.khartec.waltz.data.physical_specification.PhysicalSpecificationDao.owningEntityNameField;
import static com.khartec.waltz.schema.tables.EnumValue.ENUM_VALUE;
import static com.khartec.waltz.schema.tables.LogicalFlow.LOGICAL_FLOW;
import static com.khartec.waltz.schema.tables.PhysicalFlow.PHYSICAL_FLOW;
import static com.khartec.waltz.schema.tables.PhysicalSpecification.PHYSICAL_SPECIFICATION;


public class PhysicalFlowGenerator implements SampleDataGenerator {

    private static final Random rnd = RandomUtilities.getRandom();

    private static List<Criticality> criticalityDistribution = newArrayList(
            Criticality.NONE,
            Criticality.UNKNOWN,
            Criticality.LOW,
            Criticality.LOW,
            Criticality.LOW,
            Criticality.MEDIUM,
            Criticality.MEDIUM,
            Criticality.HIGH,
            Criticality.HIGH,
            Criticality.HIGH,
            Criticality.VERY_HIGH,
            Criticality.VERY_HIGH);


    private static List<PhysicalFlowRecord> mkPhysicalFlowRecords(PhysicalSpecification spec,
                                                                  List<Long> logicalFlowIds,
                                                                  List<String> transportKinds) {


        return IntStream.range(0, logicalFlowIds.size() - 1)
                .mapToObj(i -> {
                    Long logicalFlowId = logicalFlowIds.remove(rnd.nextInt(logicalFlowIds.size() - 1));

                    PhysicalFlowRecord record = new PhysicalFlowRecord();
                    record.setSpecificationId(spec.id().get());
                    record.setLogicalFlowId(logicalFlowId);
                    record.setDescription("Description: " + spec + " - " + logicalFlowId.toString());
                    record.setProvenance(SAMPLE_DATA_PROVENANCE);
                    record.setBasisOffset(randomPick(newArrayList(0, 0, 0, 0, 1, 1, 2, -1)));
                    record.setTransport(randomPick(transportKinds));
                    record.setFrequency(randomPick(FrequencyKind.values()).name());
                    record.setLastUpdatedBy("admin");
                    record.setCriticality(randomPick(criticalityDistribution).name());
                    return record;
                })
                .collect(Collectors.toList());
    }


    @Override
    public Map<String, Integer> create(ApplicationContext ctx) {
        DSLContext dsl = getDsl(ctx);

        List<PhysicalSpecification> specifications = dsl
                .select(PHYSICAL_SPECIFICATION.fields())
                .select(owningEntityNameField)
                .from(PHYSICAL_SPECIFICATION)
                .fetch(PhysicalSpecificationDao.TO_DOMAIN_MAPPER);

        List<String> transportKinds = dsl
                .select(ENUM_VALUE.KEY)
                .from(ENUM_VALUE)
                .where(ENUM_VALUE.TYPE.eq(EnumValueKind.TRANSPORT_KIND.dbValue()))
                .fetch(ENUM_VALUE.KEY);

        List<Tuple3<Long, Long, Long>> allLogicalFLows = dsl
                .select(
                    LOGICAL_FLOW.ID,
                    LOGICAL_FLOW.SOURCE_ENTITY_ID,
                    LOGICAL_FLOW.TARGET_ENTITY_ID)
                .from(LOGICAL_FLOW)
                .fetch(r -> Tuple.tuple(
                        r.getValue(LOGICAL_FLOW.ID),
                        r.getValue(LOGICAL_FLOW.SOURCE_ENTITY_ID),
                        r.getValue(LOGICAL_FLOW.TARGET_ENTITY_ID)));

        Map<Long, Collection<Long>> logicalFlowIdsBySourceApp = groupBy(
                t -> t.v2(),
                t -> t.v1(),
                allLogicalFLows);


        final int flowBatchSize = 100000;
        List<PhysicalFlowRecord> flowBatch = new ArrayList<PhysicalFlowRecord>((int) (flowBatchSize * 1.2));

        for (PhysicalSpecification spec : specifications) {
            Collection<Long> relatedLogicalFlowsIds = logicalFlowIdsBySourceApp.get(spec.owningEntity().id());
            if (!isEmpty(relatedLogicalFlowsIds)) {
                List<PhysicalFlowRecord> physicalFlowRecords = mkPhysicalFlowRecords(spec, new LinkedList<>(relatedLogicalFlowsIds), transportKinds);
                flowBatch.addAll(physicalFlowRecords);
            }

            if(flowBatch.size() >= flowBatchSize) {
                log(String.format("--- saving records: count: %s", flowBatch.size()));
                dsl.batchInsert(flowBatch).execute();
                flowBatch.clear();
            }
        }

        log(String.format("--- saving records: count: %s", flowBatch.size()));
        dsl.batchInsert(flowBatch).execute();
        flowBatch.clear();
        log("---done");   return null;
    }

    @Override
    public boolean remove(ApplicationContext ctx) {
        DSLContext dsl = getDsl(ctx);

        log("---removing demo records");
        dsl.deleteFrom(PHYSICAL_FLOW)
                .where(PHYSICAL_FLOW.PROVENANCE.eq(SAMPLE_DATA_PROVENANCE))
                .execute();

        return false;
    }
}
