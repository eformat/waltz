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

package com.khartec.waltz.service.physical_flow_participant;

import com.khartec.waltz.data.physical_flow_participant.PhysicalFlowParticipantDao;
import com.khartec.waltz.model.EntityReference;
import com.khartec.waltz.model.physical_flow_participant.ParticipationKind;
import com.khartec.waltz.model.physical_flow_participant.PhysicalFlowParticipant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static com.khartec.waltz.common.Checks.checkNotNull;


@Service
public class PhysicalFlowParticipantService {

    public final PhysicalFlowParticipantDao dao;


    @Autowired
    public PhysicalFlowParticipantService(PhysicalFlowParticipantDao dao) {
        checkNotNull(dao, "dao cannot be null");
        this.dao = dao;
    }


    public Collection<PhysicalFlowParticipant> findByPhysicalFlowId(long id) {
        return dao.findByPhysicalFlowId(id);
    }

    public Collection<PhysicalFlowParticipant> findByParticipant(EntityReference entityReference) {
        checkNotNull(entityReference, "entityReference cannot be null");
        return dao.findByParticipant(entityReference);
    }

    public Boolean remove(long physicalFlowId,
                          ParticipationKind participationKind,
                          EntityReference participant,
                          String username) {

        checkNotNull(participationKind, "participationKind cannot be null");

        boolean result = dao.remove(physicalFlowId, participationKind, participant);
        if (result) {
            // write changelog entry to physical flow
        }
        return result;
    }


    public Boolean add(long physicalFlowId,
                          ParticipationKind participationKind,
                          EntityReference participant,
                          String username) {

        checkNotNull(participationKind, "participationKind cannot be null");

        boolean result = dao.add(physicalFlowId, participationKind, participant, username);
        if (result) {
            // write changelog entry to physical flow
        }
        return result;
    }


}
