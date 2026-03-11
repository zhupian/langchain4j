package com.jiang.consultant.controller.dto;

public record TtsRequest(String text, String voice, Integer rate) {
}