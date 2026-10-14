package edu.uemwiki.contrib;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHash {
    public static void main(String[] args) {
        try {
            java.io.Console console=System.console();
            char[] password=console==null?new java.io.BufferedReader(new java.io.InputStreamReader(System.in,"UTF-8")).readLine().toCharArray():console.readPassword("管理员密码: ");
            if(password==null||password.length==0)throw new IllegalArgumentException("密码不能为空");
            System.out.println(new BCryptPasswordEncoder().encode(new String(password)));
            java.util.Arrays.fill(password,'\0');
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
