package com.wherehouse.members.model;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class MemberConverter {

    /**
     * MemberDTO -> MembersEntity 변환
     */
    public static MembersEntity toEntity(MemberDTO dto) {
        if (dto == null) {
            return null;
        }

        return MembersEntity.builder()
                .id(dto.getId())
                .pw(dto.getPw())
                .nickName(dto.getNickName())
                .tel(dto.getTel())
                .email(dto.getEmail())
                .joinDate(dto.getJoinDate())
                .build();
    }

    /**
     * MembersEntity -> MemberDTO 변환
     */
    public static MemberDTO toDTO(MembersEntity entity) {
        if (entity == null) {
            return null;
        }

        return new MemberDTO(
                entity.getId(),
                entity.getPw(),
                entity.getNickName(),
                entity.getTel(),
                entity.getEmail(),
                entity.getJoinDate()
        );
    }

    /**
     * List<MemberDTO> -> List<MembersEntity> 변환
     */
    public static List<MembersEntity> toEntityList(List<MemberDTO> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return List.of();
        }
        return dtoList.stream().map(MemberConverter::toEntity).collect(Collectors.toList());
    }

    /**
     * List<MembersEntity> -> List<MemberDTO> 변환
     */
    public static List<MemberDTO> toDTOList(List<MembersEntity> entityList) {
        if (entityList == null || entityList.isEmpty()) {
            return List.of();
        }
        return entityList.stream().map(MemberConverter::toDTO).collect(Collectors.toList());
    }
}
