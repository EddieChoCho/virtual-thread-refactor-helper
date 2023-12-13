## Using Docker

1. Build the project

        $ mvn clean install -Dmaven.test.skip=true
2. Build the image

        $ docker build -t virtual-thread-refactor-helper:1.0 .
3. Start the container

        $ docker run -iv DIRECTORY_OF_THE_PROJECT_NEEDS_TO_BE_REFACTOR:DIRECTORY_IN_THE_CONTAINER virtual-thread-refactor-helper:1.0
        # -i: containers to run with an allocated stdin
        # -v: mount a host directory in a Docker container
        # e.g.,
        # $ docker run -iv ./src/test/java:/usr/src/app/test/java virtual-thread-refactor-helper:1.0

4. In put the DIRECTORY_IN_THE_CONTAINER

        Dec 07, 2023 7:34:54 PM VirtualThreadRefactorHelper main
        INFO: Please input the project directory:
        $ /usr/src/app/test/java                                                
        Dec 07, 2023 7:35:18 PM VirtualThreadRefactorHelper main
        INFO: Java file modified successfully.