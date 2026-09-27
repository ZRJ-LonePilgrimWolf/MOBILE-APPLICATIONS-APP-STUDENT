-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: lab_registration
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `accounts`
--

DROP TABLE IF EXISTS `accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('lecturer','student') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `accounts`
--

LOCK TABLES `accounts` WRITE;
/*!40000 ALTER TABLE `accounts` DISABLE KEYS */;
/*!40000 ALTER TABLE `accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `groups_table`
--

DROP TABLE IF EXISTS `groups_table`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `groups_table` (
  `id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  `capacity` int NOT NULL DEFAULT '15',
  `member_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `groups_table`
--

LOCK TABLES `groups_table` WRITE;
/*!40000 ALTER TABLE `groups_table` DISABLE KEYS */;
INSERT INTO `groups_table` VALUES (1,'G01',16,15),(2,'TEST_RACE',15,15);
/*!40000 ALTER TABLE `groups_table` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `operations`
--

DROP TABLE IF EXISTS `operations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operations` (
  `operation_id` char(36) NOT NULL,
  `account_id` int NOT NULL,
  `student_id` int DEFAULT NULL,
  `type` enum('CREATE','EDIT','DELETE','GROUP_ASSIGN','GROUP_TRANSFER') NOT NULL,
  `payload_hash` char(64) NOT NULL,
  `result_json` json DEFAULT NULL,
  `status` enum('APPLIED','REJECTED','CONFLICT') NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `operations`
--

LOCK TABLES `operations` WRITE;
/*!40000 ALTER TABLE `operations` DISABLE KEYS */;
INSERT INTO `operations` VALUES ('11111111-1111-1111-1111-111111111111',1,1,'EDIT','46b4833a4bf8451835b89fd9e997fa2a212fd6d38431b1780aa55da717938f25',NULL,'REJECTED','2026-09-21 22:41:16'),('22222222-2222-2222-2222-222222222222',1,1,'EDIT','46b4833a4bf8451835b89fd9e997fa2a212fd6d38431b1780aa55da717938f25','{\"studentId\": \"1\", \"newVersion\": 2}','APPLIED','2026-09-21 22:49:27'),('44444444-4444-4444-4444-444444444444',1,1,'EDIT','6c787ed374a041efc10ad603522f14fe0238eb50ec36f28a0b64cdd15fefefa7','{\"studentId\": \"1\", \"newVersion\": 3}','APPLIED','2026-09-22 01:39:10'),('55555555-5555-5555-5555-555555555555',1,1,'EDIT','2fbad2f76270724c2bc74699b51620c3168e1d40edcff0a09f1a17a8b2573cd4','{\"studentId\": \"1\", \"newVersion\": 2}','APPLIED','2026-09-22 02:07:26'),('66666666-6666-6666-6666-666666666666',1,1,'EDIT','08830ea5c3a3bf0f2fc216e997a25c88cb8106359f8ccf5d540ad71837f5ddbc',NULL,'CONFLICT','2026-09-22 02:07:47'),('77777777-7777-7777-7777-777777777777',1,1,'DELETE','5dbdccf01f4581863a0087b9a760833521dbaaaf59dbe86f871029d76ab4871a','{\"deleted\": true, \"studentId\": \"1\"}','APPLIED','2026-09-22 02:58:48'),('88888888-8888-8888-8888-888888888888',1,1,'EDIT','cd18f7eb4a2ee1991d0987ddc1b1854a06f10128d3a935b1703e0ab85e387691',NULL,'REJECTED','2026-09-22 03:00:13');
/*!40000 ALTER TABLE `operations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `students`
--

DROP TABLE IF EXISTS `students`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `students` (
  `id` int NOT NULL AUTO_INCREMENT,
  `account_id` int NOT NULL,
  `first_name` varchar(100) DEFAULT NULL,
  `last_name` varchar(100) DEFAULT NULL,
  `group_id` int DEFAULT NULL,
  `version` int NOT NULL DEFAULT '1',
  `deleted_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `group_id` (`group_id`),
  CONSTRAINT `students_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `groups_table` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `students`
--

LOCK TABLES `students` WRITE;
/*!40000 ALTER TABLE `students` DISABLE KEYS */;
INSERT INTO `students` VALUES (1,1,'Original','Name',2,2,'2026-09-22 02:58:48','2026-09-22 02:58:48');
/*!40000 ALTER TABLE `students` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-27 18:38:52
