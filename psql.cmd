@echo off
"D:\PostgreSQL\9.2\bin\psql.exe" -h localhost -U postgres -d exdb -p 5432 %*
