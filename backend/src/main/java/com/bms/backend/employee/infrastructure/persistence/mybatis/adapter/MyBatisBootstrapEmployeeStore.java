package com.bms.backend.employee.infrastructure.persistence.mybatis.adapter;

import com.bms.backend.employee.application.port.out.BootstrapEmployeeStore;
import com.bms.backend.employee.infrastructure.persistence.mybatis.mapper.BootstrapEmployeeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisBootstrapEmployeeStore implements BootstrapEmployeeStore {

    private final BootstrapEmployeeMapper mapper;

    @Override
    public boolean existsByEmployeeNumber(String employeeNumber) {
        return mapper.countByEmployeeNumber(employeeNumber) > 0;
    }

    @Override
    public void createResource(NewResource resource) {
        requireSingleInsert(
                mapper.insertResource(
                        resource.resourceId(),
                        resource.fullName(),
                        resource.mobileNumber(),
                        resource.emailAddress(),
                        resource.registeredBy(),
                        resource.registeredAt(),
                        resource.taskTargetId()),
                "BOOTSTRAP_RESOURCE_INSERT_COUNT_INVALID");
    }

    @Override
    public void createEmployee(NewEmployee employee) {
        requireSingleInsert(
                mapper.insertEmployee(
                        employee.resourceId(),
                        employee.employeeNumber(),
                        employee.organizationId(),
                        employee.registeredBy(),
                        employee.registeredAt()),
                "BOOTSTRAP_EMPLOYEE_INSERT_COUNT_INVALID");
    }

    private void requireSingleInsert(int inserted, String errorCode) {
        if (inserted != 1) {
            throw new DataIntegrityViolationException(errorCode);
        }
    }
}
