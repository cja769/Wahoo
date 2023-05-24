sudo update-alternatives --set java /usr/lib/jvm/java-17-openjdk-amd64/bin/java && ./gradlew bootrun --args='--spring.profiles.active=production' > ~/logs/wahoo_be.log 2>&1 &
