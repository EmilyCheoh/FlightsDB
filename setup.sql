CREATE TABLE Customer (
    customerID INT IDENTITY(1,1) PRIMARY KEY NOT NULL,
    fullname VARCHAR(100),
    handle VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL
);

CREATE TABLE Reservation (
    custID INT REFERENCES Customer(customerID),
    flightID INT REFERENCES Flights(fid),
    PRIMARY KEY (custID, flightID)
)

-- 1
INSERT INTO Customer
VALUES ('Emily Qiao', 'e', 123); 

-- 2
INSERT INTO Customer
VALUES ('Silvia Wang', 's', 123);

-- 3
INSERT INTO Customer
VALUES ('Kara', 'k', 123);

-- 4
INSERT INTO Customer
VALUES ('Alex', 'a', 123);


INSERT INTO Reservation
VALUES (1, 2);

INSERT INTO Reservation
VALUES (2, 333);

INSERT INTO Reservation
VALUES (2, 222);


-- fid 708454, 2015-7-10, flight num 734
INSERT INTO Reservation
VALUES (2, 708454);

INSERT INTO Reservation
VALUES (3, 708454);

INSERT INTO Reservation
VALUES (4, 708454);
