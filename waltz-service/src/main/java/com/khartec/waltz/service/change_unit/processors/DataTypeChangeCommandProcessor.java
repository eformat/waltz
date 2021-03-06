/*
 * Waltz - Enterprise Architecture
 * Copyright (C) 2016, 2017  Waltz open source project
 * See README.md for more information
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

package com.khartec.waltz.service.change_unit.processors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khartec.waltz.common.SetUtilities;
import com.khartec.waltz.model.EntityKind;
import com.khartec.waltz.model.attribute_change.AttributeChange;
import com.khartec.waltz.model.change_unit.ChangeUnit;
import com.khartec.waltz.model.physical_flow.PhysicalFlow;
import com.khartec.waltz.service.change_unit.AttributeChangeCommandProcessor;
import com.khartec.waltz.service.physical_flow.PhysicalFlowService;
import com.khartec.waltz.service.physical_specification_data_type.PhysicalSpecDataTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.common.Checks.checkTrue;
import static com.khartec.waltz.common.SetUtilities.minus;
import static java.util.stream.Collectors.toSet;


@Service
public class DataTypeChangeCommandProcessor implements AttributeChangeCommandProcessor {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final PhysicalFlowService physicalFlowService;
    private final PhysicalSpecDataTypeService physicalSpecDataTypeService;


    @Autowired
    public DataTypeChangeCommandProcessor(PhysicalFlowService physicalFlowService,
                                          PhysicalSpecDataTypeService physicalSpecDataTypeService) {
        checkNotNull(physicalFlowService, "physicalFlowService cannot be null");
        checkNotNull(physicalSpecDataTypeService, "physicalSpecDataTypeService cannot be null");

        this.physicalFlowService = physicalFlowService;
        this.physicalSpecDataTypeService = physicalSpecDataTypeService;
    }


    @Override
    public String supportedAttribute() {
        return "DataType";
    }


    @Override
    public boolean apply(AttributeChange attributeChange,
                                                               ChangeUnit changeUnit,
                                                               String userName) {
        doBasicValidation(attributeChange, changeUnit, userName);
        checkTrue(changeUnit.subjectEntity().kind() == EntityKind.PHYSICAL_FLOW,
                "Change Subject should be a Physical Flow");

        // get physical flow
        PhysicalFlow physicalFlow = physicalFlowService.getById(changeUnit.subjectEntity().id());

        // update the specs data types
        Set<Long> oldValues = readValue(attributeChange.oldValue());
        Set<Long> newValues = readValue(attributeChange.newValue());

        Set<Long> existing = physicalSpecDataTypeService.findBySpecificationId(physicalFlow.specificationId())
                .stream()
                .map(a -> a.dataTypeId())
                .collect(toSet());

        Set<Long> toAdd = minus(newValues, oldValues, existing);
        Set<Long> toRemove = minus(oldValues, newValues);


        int[] removedCount = physicalSpecDataTypeService.removeDataTypes(userName, physicalFlow.specificationId(), toRemove);
        int[] addedCount = physicalSpecDataTypeService.addDataTypes(userName, physicalFlow.specificationId(), toAdd);

        return removedCount.length == toRemove.size() && addedCount.length == toAdd.size();
    }


    private Set<Long> readValue(String val) {
        try {
            List<HashMap> list = JSON_MAPPER.readValue(val, List.class);
            Set<Long> dataTypeId = list.stream()
                    .map(hm -> hm.get("dataTypeId"))
                    .map(d -> Long.valueOf(d.toString()))
                    .collect(toSet());
            return dataTypeId;
        } catch (IOException e) {
            return SetUtilities.asSet();
        }
    }
}
