package com.wow.be.tc.schedule.impl;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleJobDetailEntityId implements Serializable {

	private static final long serialVersionUID = 1L;

	@Column(name="SCHEDULE_JOB_ID")
	private String scheduleJobId;
	
	@Column(name="ENTRY_KEY")
	private String entryKey;
	
	public boolean equals(Object object) {
        if (object instanceof ScheduleJobDetailEntityId) {
        	ScheduleJobDetailEntityId pk = (ScheduleJobDetailEntityId)object;
            return entryKey.equals(pk.entryKey) && scheduleJobId.equals(pk.scheduleJobId);
        } else {
            return false;
        }
    }
  
    public int hashCode() {
        return scheduleJobId.hashCode() + entryKey.hashCode();
    }
}
