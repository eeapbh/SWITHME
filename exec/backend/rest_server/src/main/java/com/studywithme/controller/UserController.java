package com.studywithme.controller;

import java.io.IOException;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.rowset.serial.SerialException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studywithme.DtoOnlyReturn.UserDto;
import com.studywithme.config.CommonMethods;
import com.studywithme.config.JwtService;
import com.studywithme.entity.TimeMonthly;
import com.studywithme.entity.UserInfo;
import com.studywithme.repository.DefaultProfileImgRepository;
import com.studywithme.repository.TimeMonthlyRepository;
import com.studywithme.repository.UserRepository;

import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/user")
public class UserController {
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	private JwtService jwtService;
	
	@Autowired
	CommonMethods commonMethods;
	
	@Autowired
	DefaultProfileImgRepository defaultProfileImgRepository;
	
	@Autowired
	TimeMonthlyRepository timeMonthlyRepository;
	
	@PostMapping("/signup")
	@ApiOperation(value="????????????",notes="User ????????? body??? ?????? db??? ??????\n?????????????????? ??????")
	public Object createUser(@RequestBody UserInfo user) {
		Map<String,Object> result=new HashMap<>();
		
		if(!userRepository.findByUserNickname(user.getUserNickname()).isPresent()) {
			String hashed=commonMethods.getHashed(user.getUserPassword());
			if(hashed!=null) {
				user.setUserPassword(hashed);
				user.setUserProfileImg(defaultProfileImgRepository.findById(1).get().getDefaultProfileImgData());
				userRepository.save(user);
				result.put("success",true);
			}
			else
				result.put("success",false);
		}
		else 
			result.put("success",false);
		
		return result;
	}
	
	@PostMapping("/signup-social")
	@ApiOperation(value="????????????(??????)", notes="?????? ??????????????? ?????? ???????????? ??? ?????? ????????????\n???????????? ???????????? ?????? ?????? ????????? ??????")
	public Object createUserSocial(@RequestBody Map<String, String> map) {
		Map<String, Object> result = new HashMap<>();
		
		String nickname = map.get("nickname");
		String token = map.get("token");
	
		UserInfo userNew = new ObjectMapper().convertValue(jwtService.get(token).get("User"), UserInfo.class);
		UserInfo user=new UserInfo();
		
		user.setUserNickname(nickname);
		user.setUserId(userNew.getUserId());
		user.setUserType(userNew.getUserType());

		user.setUserProfileImg(defaultProfileImgRepository.findById(1).get().getDefaultProfileImgData());
		
		UserInfo userInfo=userRepository.save(user);
		String jwtToken = jwtService.create(userInfo);
		result.put("success", true);
		result.put("token", jwtToken);
		
		return result;
	}
	
	@PostMapping("/login")
	@ApiOperation(value="?????????",notes="id??? password??? ??????????????? ?????? ????????? jwt ??????\n?????????????????? ??????")
	public Object loginUser(@RequestParam("userId") String userId,
			@RequestParam("userPassword") String userPassword, HttpServletResponse resp) {

		Map<String,Object> result=new HashMap<>();
		
		String hashed=commonMethods.getHashed(userPassword);
		Optional<UserInfo> user=userRepository.findByUserIdAndUserPassword(userId, hashed);
		if(user.isPresent()) {
			String token=jwtService.create(user.get());
			resp.setHeader("jwt-auth-token",token);
			result.put("success",true);
		}
		else
			result.put("success",false);
		
		return result;
	}
	
	@GetMapping("/id")
	@ApiOperation(value="id ????????????",notes="id??? ??????????????? ?????? ????????????\n?????????????????? ??????")
	public Object checkIdDuplicated(@RequestParam("userId") String userId) {
		Map<String,Object> result=new HashMap<>();
		if(userRepository.findById(userId).isPresent())
			result.put("isPresent",true);
		else
			result.put("isPresent",false);
		
		return result;
	}
	
	@GetMapping("/nickname")
	@ApiOperation(value="????????? ????????????",notes="???????????? ??????????????? ?????? ????????????\n?????????????????? ??????")
	public Object checkNicknameDuplicated(String userNickname) {
		Map<String,Object> result=new HashMap<>();
		if(userRepository.findByUserNickname(userNickname).isPresent())
			result.put("isPresent",true);
		else
			result.put("isPresent",false);
		
		return result;
	}
	
	@GetMapping("/email")
	@ApiOperation(value="??????????????? ????????? ??????",notes="???????????? ??????????????? ?????? ????????? ?????? ???????????? response??? ??????\n?????????????????? ??????")
	public Object emailVaild(@RequestParam("userEmail") String userEmail) throws AddressException, MessagingException {
		Map<String,Object> result=new HashMap<>();
		String host="smtp.naver.com";
		final String username="swithmedev";
		final String password="swithme103";
		int port=465;
		
		Optional<UserInfo> user=userRepository.findById(userEmail);
		if(!user.isPresent()) {
			String subject="???????????? ?????????????????????.";
					
			int validNum=(int)(Math.random()*10000);
			String body="????????? ??????????????? ??????????????????!\n"+Integer.toString(validNum);
			
			Properties props=System.getProperties();
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port",port);
			props.put("mail.smtp.auth","true");
			props.put("mail.smtp.ssl.enable","true");
			props.put("mail.smtp.ssl.trust",host);
			
			Session session=Session.getDefaultInstance(props,new javax.mail.Authenticator() {
				String un=username;
				String pw=password;
				protected javax.mail.PasswordAuthentication getPasswordAuthentication(){
					return new javax.mail.PasswordAuthentication(un,pw);
				}
			});
			session.setDebug(true);
			Message mimeMessage=new MimeMessage(session);
			mimeMessage.setFrom(new InternetAddress("swithmedev@naver.com"));
			mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(userEmail));
			
			mimeMessage.setSubject(subject);
			mimeMessage.setText(body);
			Transport.send(mimeMessage);
			
			result.put("success",true);
			result.put("validNum",validNum);
		}
		else
			result.put("success",false);
		return result;
	}
	
	@PostMapping("/password")
	@ApiOperation(value="???????????? ??????",notes="???????????? ??????????????? ?????? ????????? ?????? ??????\n?????????????????? ??????")
	public Object sendEmail(@RequestParam("userEmail") String userEmail) throws AddressException, MessagingException {
		Map<String,Object> result=new HashMap<>();
		String host="smtp.naver.com";
		final String username="swithmedev";
		final String password="swithme103";
		int port=465;
		
		Optional<UserInfo> user=userRepository.findById(userEmail);
		if(user.isPresent()) {
			String subject="???????????? ?????? ???????????????.";
					
			String token=jwtService.create(user.get());
			
			String link="https://j4b103.p.ssafy.io/my-page-modify?jwt="+token;
			String body="??????????????? "+user.get().getUserNickname()+"???!\n"+
					"?????? ????????? ??????????????? ??????????????? ????????? ??? ????????????.\n"+
					link;
			
			Properties props=System.getProperties();
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.port",port);
			props.put("mail.smtp.auth","true");
			props.put("mail.smtp.ssl.enable","true");
			props.put("mail.smtp.ssl.trust",host);
			
			Session session=Session.getDefaultInstance(props,new javax.mail.Authenticator() {
				String un=username;
				String pw=password;
				protected javax.mail.PasswordAuthentication getPasswordAuthentication(){
					return new javax.mail.PasswordAuthentication(un,pw);
				}
			});
			session.setDebug(true);
			Message mimeMessage=new MimeMessage(session);
			mimeMessage.setFrom(new InternetAddress("swithmedev@naver.com"));
			mimeMessage.setRecipient(Message.RecipientType.TO, new InternetAddress(userEmail));
			
			mimeMessage.setSubject(subject);
			mimeMessage.setText(body);
			Transport.send(mimeMessage);
			
			result.put("success",true);
		}
		else
			result.put("success",false);
		return result;
	}
	
	@PutMapping("/password")//??????????????? ????????? -> ????????? ?????????????????? ?????? ??????????????? ????????? ?????????????
	@ApiOperation(value="???????????? ??????",notes="jwt ????????? ?????? password??? ??????????????? ?????? db ????????????\n?????????????????? ???????????????????")
	public Object changePassword(@RequestParam("jwt") String token,@RequestParam("newPassword") String newPassword) {
		Map<String,Object> result=new HashMap<>();
		
		result.put("success",false);
		
		String nickname=commonMethods.getUserNickname(token);
	
		Optional<UserInfo> user=userRepository.findByUserNickname(nickname);
		if(user.isPresent()) {
			user.get().setUserPassword(commonMethods.getHashed(newPassword));
			userRepository.save(user.get());
			
			result.clear();
			result.put("success",true);
		}
		
		return result;
	}
	
	@PutMapping("/nickname")
	@ApiOperation(value="????????? ??????",notes="??????????????? ????????? ???????????? ?????? ???????????? jwt?????? ??????")
	public Object changeNickname(@RequestParam String newNickname,HttpServletRequest req,HttpServletResponse resp) {
		Map<String,Object> result=new HashMap<>();
		result.put("success",false);
		
		String oldNickname=commonMethods.getUserNickname(req.getHeader("jwt-auth-token"));
		
		Optional<UserInfo> user=userRepository.findByUserNickname(oldNickname);
		if(user.isPresent()) {
			user.get().setUserNickname(newNickname);
			userRepository.save(user.get());
			String jwt=jwtService.create(user.get());
			
			resp.setHeader("jwt-auth-token", jwt);
			result.clear();
			result.put("success",true);
		}
		
		return result;
	}

	@GetMapping("")
	@ApiOperation(value="???????????? ????????????",notes="????????? jwt??? ???????????? ????????? ?????? ??????")
	public Object findUser(HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();

		String id=commonMethods.getUserId(req.getHeader("jwt-auth-token"));
		
		Optional<UserInfo> user=userRepository.findById(id);
		if(user.isPresent()) {
			user.get().setUserPassword(null);
//			user.setProfileImg(user.get().getUserProfileImg().getBytes(1l, (int)user.get().getUserProfileImg().length()));
			try {
				result.put("profileImg",user.get().getUserProfileImg().getBytes(1l, (int)user.get().getUserProfileImg().length()));
				user.get().setUserProfileImg(null);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			result.put("data",user.get());
		}
		else
			result.put("data",null);
		
		return result;
	}
	
	@PostMapping("/mypage")
	@ApiOperation(value="?????????????????? ???????????? ?????? ???????????? ??????",notes="??????????????? ??????????????? true ??????")
	public Object showMypage(@RequestParam("userPassword") String userPassword,HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();
		
		String id=commonMethods.getUserId(req.getHeader("jwt-auth-token"));
		String password=commonMethods.getHashed(userPassword);
		
		Optional<UserInfo> user=userRepository.findByUserIdAndUserPassword(id, password);
		if(user.isPresent()) 
			result.put("isCorrect",true);
		else
			result.put("isCorrect",false);
		
		return result;
	}
	
	@PutMapping("/profile-img")
	@ApiOperation(value="????????? ????????? ??????",notes="????????? ???????????? ????????? ??? ??????????????? true ??????")
	public Object changeProfileImg(@RequestParam("file") MultipartFile file,HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();
		String id=commonMethods.getUserId(req.getHeader("jwt-auth-token"));
		
		byte[] bytes;

		result.put("result", false);
		Optional<UserInfo> user = userRepository.findById(id);
		if (user.isPresent()) {
			try {
				bytes = file.getBytes();
				bytes=commonMethods.resize(bytes);
				try {
					Blob blob = new javax.sql.rowset.serial.SerialBlob(bytes);

					user.get().setUserProfileImg(blob);
					userRepository.save(user.get());
					result.clear();
					result.put("result", true);
				} catch (SerialException e1) {
					e1.printStackTrace();
				} catch (SQLException e1) {
					e1.printStackTrace();
				}
			} catch (IOException e2) {
				e2.printStackTrace();
			}
		}
		return result;
	}
	
	@PutMapping("/message")
	@ApiOperation(value="??????????????? ??????",notes="?????????????????? ????????? ??? ??????????????? true ??????")
	public Object changeMessage(@RequestParam("message") String message,HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();
		
		String id=commonMethods.getUserId(req.getHeader("jwt-auth-token"));
		Optional<UserInfo> user=userRepository.findById(id);
		if(user.isPresent()) {
			user.get().setUserMessage(message);
			userRepository.save(user.get());
			result.put("success",true);
		}
		else
			result.put("success",false);
		
		return result;
	}
	
	@PutMapping("/start")
	@ApiOperation(value="???????????? ??????",notes="???????????? ????????? ??????????????? ??????")
	public Object startStudy(HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();
		
		String nickname=commonMethods.getUserNickname(req.getHeader("jwt-auth-token"));
		
		result.put("success",false);
		
		Optional<UserInfo> user=userRepository.findByUserNickname(nickname);
		if(user.isPresent()) {
			user.get().setUserIsStudying(true);
			userRepository.save(user.get());
			
			result.clear();
			result.put("success",true);
		}
		return result;
	}
	
	@PutMapping("/end")
	@ApiOperation(value="???????????? ??????",notes="???????????? ????????? ???????????? ???????????? ??????")
	public Object endStudy(HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();
		
		String nickname=commonMethods.getUserNickname(req.getHeader("jwt-auth-token"));
		
		result.put("success",false);
		
		Optional<UserInfo> user=userRepository.findByUserNickname(nickname);
		if(user.isPresent()) {
			user.get().setUserIsStudying(false);
			userRepository.save(user.get());
			
			result.clear();
			result.put("success",true);
		}
		return result;
	}
		
	@GetMapping("/ranking")
	@ApiOperation(value="?????? ?????????",notes="??????????????? ?????? datetime??? ?????? ?????? ?????? ?????? ??????")
	public Object getAllRanking(@RequestParam("datetime") String datetime, HttpServletRequest req) {
		Map<String,Object> result=new HashMap<>();
		
		result.put("allRankingList",null);
		
		datetime=datetime.substring(2,7);
		datetime=datetime.replaceAll("-", "");
		
		Optional<List<TimeMonthly>> timeMonthlyList=timeMonthlyRepository.findByTimeMonthlyYearMonth(datetime, Sort.by("timeMonthlyTime").descending());
		if(timeMonthlyList.isPresent()) {
			List<UserDto> userList=new ArrayList<>();
			for(TimeMonthly tm:timeMonthlyList.get()) {
				Optional<UserInfo> user=userRepository.findByUserNickname(tm.getTimeMonthlyUserNickname());
				if(user.isPresent()) {
					UserDto userDto=new UserDto();
					userDto.setNickname(user.get().getUserNickname());
					try {
						userDto.setProfileImg(user.get().getUserProfileImg().getBytes(1l, (int)user.get().getUserProfileImg().length()));
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					userDto.setTodayStudyTime(tm.getTimeMonthlyTime());
					
					userList.add(userDto);
				}
			}
			
			int myStudyTimeCurMonth=0;
			String nickname=commonMethods.getUserNickname(req.getHeader("jwt-auth-token"));
			Optional<TimeMonthly> timeMonthly=timeMonthlyRepository.findByTimeMonthlyUserNicknameAndTimeMonthlyYearMonth(nickname, datetime);
			
			if(timeMonthly.isPresent())
				myStudyTimeCurMonth=timeMonthly.get().getTimeMonthlyTime();
			
			String myMessage="";
			Optional<UserInfo> user=userRepository.findByUserNickname(nickname);
			if(user.isPresent())
				myMessage=user.get().getUserMessage();
			
			int myRank=0;
			for(int i=0;i<userList.size();i++) {
				if(userList.get(i).getNickname().equals(nickname)) {
					myRank=i+1;
					break;
				}
			}
			result.clear();
			result.put("myStudyTime",myStudyTimeCurMonth);
			result.put("myMessage",myMessage);
			result.put("myRank",myRank);
			result.put("allRankingList",userList);
		}
		return result;
	}
}
