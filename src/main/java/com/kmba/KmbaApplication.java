package com.kmba;

import com.kmba.cli.CliHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KmbaApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "--help".equals(args[0])) {
            System.out.println(CliHandler.HELP);
            return;
        }
        if (args.length > 0 && "cli".equals(args[0])) {
            String[] cliArgs = new String[args.length - 1];
            System.arraycopy(args, 1, cliArgs, 0, args.length - 1);
            try {
                CliHandler.handle(cliArgs);
            } catch (Exception e) {
                System.err.println("CLI error: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        SpringApplication.run(KmbaApplication.class, args);
    }

}
