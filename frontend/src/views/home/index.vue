<template>
  <div class="home">
    <el-container style="height: 100vh">
      <el-aside width="220px">
        <!-- TODO(W2)：动态菜单（IAM 菜单树驱动），五角色各见各的菜单 -->
        <el-menu default-active="1">
          <el-menu-item index="1">工作台（骨架）</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main>
        <h3>招聘面试系统 · 骨架页（Owner：C）</h3>
        <p>动态菜单、36 个业务页面按 W2~W13 排期逐域实现。</p>
        <el-button type="primary" @click="$router.push('/room/demo')">进入房间页骨架</el-button>
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts"></script>
