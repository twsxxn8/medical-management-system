-- ҽ�ƹ���ϵͳ���ݿ��ṹ

CREATE DATABASE medical_system;
GO

USE medical_system;
GO

-- 1. �û���
CREATE TABLE [user] (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    username NVARCHAR(50) NOT NULL UNIQUE,
    password NVARCHAR(100) NOT NULL,
    real_name NVARCHAR(50),
    role NVARCHAR(20) NOT NULL DEFAULT 'USER', -- ADMIN, DOCTOR, PATIENT, RECEPTIONIST
    phone NVARCHAR(20),
    email NVARCHAR(100),
    status INT NOT NULL DEFAULT 1, -- 0:���ã�1:����
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- 2. ���߱�
CREATE TABLE patient (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    user_id BIGINT NOT NULL UNIQUE,
    id_card NVARCHAR(18) NOT NULL UNIQUE,
    gender NVARCHAR(10), -- �У�Ů
    birth_date DATE,
    address NVARCHAR(200),
    emergency_contact NVARCHAR(50),
    emergency_phone NVARCHAR(20),
    medical_history NVARCHAR(2000), -- ��Ϊ NVARCHAR(2000) ��������
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [user](id)
);
GO

-- 3. ���ұ�
CREATE TABLE department (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(100) NOT NULL,
    parent_id BIGINT,
    description NVARCHAR(500),
    location NVARCHAR(200),
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- 4. ҽ����
CREATE TABLE doctor (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    user_id BIGINT NOT NULL UNIQUE,
    department_id BIGINT NOT NULL,
    title NVARCHAR(50), -- ����ҽʦ��������ҽʦ��
    specialty NVARCHAR(100),
    consultation_fee DECIMAL(10,2),
    introduction NVARCHAR(2000), -- ��Ϊ NVARCHAR(2000)
    schedule NVARCHAR(2000), -- JSON ��ʽ�洢�Ű���Ϣ����Ϊ NVARCHAR(2000)
    status INT NOT NULL DEFAULT 1, -- 0:ͣ�1:Ӧ��
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [user](id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);
GO

-- 5. ԤԼ�Һű�
CREATE TABLE appointment (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    time_slot NVARCHAR(50) NOT NULL, -- ���磬���磬����
    status INT NOT NULL DEFAULT 0, -- 0:��ȷ��, 1:��ȷ��, 2:�����, 3:��ȡ��, 4:�ѹ���
    reason NVARCHAR(500),
    remark NVARCHAR(500),
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (patient_id) REFERENCES patient(id),
    FOREIGN KEY (doctor_id) REFERENCES doctor(id),
    FOREIGN KEY (department_id) REFERENCES department(id)
);
GO

-- 6. ������
CREATE TABLE medical_record (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_id BIGINT,
    diagnosis NVARCHAR(MAX) NOT NULL, -- ������ݿ��ܽϳ������� MAX
    prescription NVARCHAR(MAX), -- �������ݿ��ܽϳ�
    symptoms NVARCHAR(MAX), -- ֢״�������ܽϳ�
    visit_date DATETIME2 NOT NULL DEFAULT GETDATE(),
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (patient_id) REFERENCES patient(id),
    FOREIGN KEY (doctor_id) REFERENCES doctor(id),
    FOREIGN KEY (appointment_id) REFERENCES appointment(id)
);
GO

-- 7. ҩƷ��
CREATE TABLE medicine (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(100) NOT NULL,
    category NVARCHAR(50),
    specification NVARCHAR(100),
    unit NVARCHAR(20),
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    manufacturer NVARCHAR(200),
    approval_number NVARCHAR(100), -- ��׼�ĺ�
    storage_condition NVARCHAR(200), -- ��������
    status INT NOT NULL DEFAULT 1, -- 0:�¼ܣ�1:�ϼ�
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    update_time DATETIME2 NOT NULL DEFAULT GETDATE()
);
GO

-- 8. ҩƷ������¼��
CREATE TABLE medicine_stock_log (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    medicine_id BIGINT NOT NULL,
    type INT NOT NULL, -- 1:��⣬2:����
    quantity INT NOT NULL,
    operator_id BIGINT NOT NULL,
    remark NVARCHAR(500),
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (medicine_id) REFERENCES medicine(id),
    FOREIGN KEY (operator_id) REFERENCES [user](id)
);
GO

-- 9. ϵͳ��־��
CREATE TABLE system_log (
    id BIGINT PRIMARY KEY IDENTITY(1,1),
    user_id BIGINT,
    operation NVARCHAR(100),
    method NVARCHAR(100),
    params NVARCHAR(2000), -- ��Ϊ NVARCHAR(2000)���������
    ip_address NVARCHAR(50),
    duration BIGINT, -- ����
    create_time DATETIME2 NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (user_id) REFERENCES [user](id)
);
GO

-- ��������
CREATE INDEX idx_user_username ON [user](username);
GO
CREATE INDEX idx_patient_user_id ON patient(user_id);
GO
CREATE INDEX idx_patient_id_card ON patient(id_card);
GO
CREATE INDEX idx_doctor_department ON doctor(department_id);
GO
CREATE INDEX idx_doctor_status ON doctor(status);
GO
CREATE INDEX idx_appointment_patient ON appointment(patient_id);
GO
CREATE INDEX idx_appointment_doctor ON appointment(doctor_id);
GO
CREATE INDEX idx_appointment_date ON appointment(appointment_date);
GO
CREATE INDEX idx_appointment_status ON appointment(status);
GO
CREATE INDEX idx_medical_record_patient ON medical_record(patient_id);
GO
CREATE INDEX idx_medicine_name ON medicine(name);
GO
CREATE INDEX idx_system_log_user ON system_log(user_id);
GO
CREATE INDEX idx_system_log_time ON system_log(create_time);
GO

