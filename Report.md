# Computer Networks Programming Assignment

[toc]

## Team

| Student Name          | ID   |
| --------------------- | ---- |
| Hassan Kahlid El Sawy | 6406 |
| Ramez Gerges          | 6135 |
| Ziyad Mohamed         | 6474 |

## Sample Runs
Commands file:
![image-20220515180114782](C:\Users\polit\AppData\Roaming\Typora\typora-user-images\image-20220515180114782.png)

Buidling the project * :
`gradlew build`

Running the client *:
`./gradlew run --args="build/resources/main/commands.txt" -PchooseMain=client.HttpClient`

Client Logs
![image-20220515180409046](C:\Users\polit\AppData\Roaming\Typora\typora-user-images\image-20220515180409046.png)

Running the server *:
`./gradlew run --args="80" -PchooseMain=server.FileServer`
![image-20220515180432135](C:\Users\polit\AppData\Roaming\Typora\typora-user-images\image-20220515180432135.png)
*  when executing any of the above commands  in IntelliJ after typing the command press `CTRL+SHIFT+ENTER`.

## Heuristic
$$
max(0, 1 - \text{Thread Count} * 1.0 / \text{threshold}) * \text{maxTimeoutMillis})
$$

