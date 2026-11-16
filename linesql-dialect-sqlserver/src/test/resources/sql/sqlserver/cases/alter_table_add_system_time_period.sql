ALTER TABLE dbo.employees
ADD valid_from datetime2 GENERATED ALWAYS AS ROW START HIDDEN
      CONSTRAINT df_employees_valid_from DEFAULT SYSUTCDATETIME(),
    valid_to datetime2 GENERATED ALWAYS AS ROW END HIDDEN
      CONSTRAINT df_employees_valid_to DEFAULT CONVERT(datetime2, '9999-12-31'),
    PERIOD FOR SYSTEM_TIME (valid_from, valid_to);
