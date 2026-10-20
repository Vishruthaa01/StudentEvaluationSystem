create database if not exists kec_db;
use kec_db;

drop table if exists assessment_marks;
drop table if exists enrollments;
drop table if exists courses;
drop table if exists students;

create table students (
    roll_no varchar(20) primary key,
    name varchar(100) not null,
    dept varchar(30) not null,
    semester int not null,
    constraint chk_student_sem check (semester between 1 and 8)
);

create table courses (
    course_code varchar(20) primary key,
    title varchar(100) not null,
    credits int not null,
    course_type enum('THEORY','PRACTICAL','EMBEDDED') not null,
    semester int not null,
    constraint chk_course_credits check (credits between 0 and 6),
    constraint chk_course_sem check (semester between 1 and 8)
);

create table enrollments (
    enroll_id int auto_increment primary key,
    roll_no varchar(20) not null,
    course_code varchar(20) not null,
    semester int not null,
    total_classes int not null default 0,
    attended_classes int not null default 0,
    condonation_approved boolean not null default false,
    constraint fk_enr_student foreign key (roll_no) references students(roll_no) on delete cascade on update cascade,
    constraint fk_enr_course foreign key (course_code) references courses(course_code) on delete cascade on update cascade,
    constraint uq_enrollment unique (roll_no, course_code, semester),
    constraint chk_enr_sem check (semester between 1 and 8),
    constraint chk_enr_attendance check (total_classes >= 0 and attended_classes >= 0 and attended_classes <= total_classes)
);

create table assessment_marks (
    enroll_id int primary key,
    cia1 decimal(5,2) null,
    cia2 decimal(5,2) null,
    assignment decimal(5,2) null,
    ese decimal(5,2) null,
    ese_absent boolean not null default false,
    constraint fk_marks_enrollment foreign key (enroll_id) references enrollments(enroll_id) on delete cascade,
    constraint chk_cia1 check (cia1 is null or cia1 between 0 and 100),
    constraint chk_cia2 check (cia2 is null or cia2 between 0 and 100),
    constraint chk_assignment check (assignment is null or assignment between 0 and 100),
    constraint chk_ese check (ese is null or ese between 0 and 100)
);

show tables;